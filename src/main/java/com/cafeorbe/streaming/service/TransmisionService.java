package com.cafeorbe.streaming.service;

import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.contracts.eventos.TransmisionDetenida;
import com.cafeorbe.contracts.eventos.TransmisionIniciada;
import com.cafeorbe.streaming.api.ErrorDeNegocio;
import com.cafeorbe.streaming.api.UsuarioActual;
import com.cafeorbe.streaming.client.SubastasClient;
import com.cafeorbe.streaming.client.SubastasClient.SubastaInfo;
import com.cafeorbe.streaming.outbox.OutboxWriter;
import com.cafeorbe.streaming.provider.ProveedorDeVideo;
import com.cafeorbe.streaming.provider.ProveedorDeVideo.Credenciales;
import com.cafeorbe.streaming.provider.ProveedorDeVideo.EventoDeSala;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

/** HU-11: iniciar y detener la transmisión, y entregar credenciales de la sala de video. */
@Service
public class TransmisionService {

    private static final Logger log = LoggerFactory.getLogger(TransmisionService.class);

    public record Estado(boolean transmitiendo, String sala) {
    }

    private final TransmisionRepository transmisiones;
    private final ProveedorDeVideo video;
    private final SubastasClient subastas;
    private final OutboxWriter eventos;
    private final Clock reloj;

    public TransmisionService(TransmisionRepository transmisiones, ProveedorDeVideo video, SubastasClient subastas,
                              OutboxWriter eventos, Clock reloj) {
        this.transmisiones = transmisiones;
        this.video = video;
        this.subastas = subastas;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    /**
     * Marca la transmisión como activa y devuelve credenciales de emisor. Idempotente si ya estaba activa.
     * Solo el Subastador dueño de la subasta puede transmitir, y solo si la subasta no ha terminado
     * (hallazgos 11 y 17).
     */
    @Transactional
    public Credenciales iniciar(UUID subastaId, UsuarioActual usuario) {
        usuario.exigirSubastador();
        SubastaInfo subasta = subastas.consultar(subastaId, usuario);
        if (!usuario.id().equals(subasta.subastadorId())) {
            throw ErrorDeNegocio.prohibido("Solo el Subastador de esta subasta puede transmitir");
        }
        if (subasta.terminada()) {
            throw ErrorDeNegocio.conflicto("La subasta ya terminó; no se puede transmitir");
        }

        Transmision t = transmisiones.findById(subastaId).orElseGet(() -> new Transmision(subastaId, usuario.id()));
        boolean cambio = !t.isActiva();
        if (cambio) {
            t.iniciar(usuario.id(), reloj.instant());
            transmisiones.save(t);
            eventos.registrar(Eventos.TRANSMISION_INICIADA,
                    new TransmisionIniciada(subastaId, video.nombreDeSala(subastaId)));
        }
        return video.credencialesDeEmisor(subastaId, usuario.id(), usuario.nombre());
    }

    @Transactional
    public Estado detener(UUID subastaId, UsuarioActual usuario) {
        usuario.exigirSubastador();
        Transmision t = transmisiones.findById(subastaId).orElse(null);
        if (t == null || !t.isActiva()) {
            return new Estado(false, video.nombreDeSala(subastaId));
        }
        exigirDueno(t, usuario);
        detenerYAvisar(t);
        return new Estado(false, video.nombreDeSala(subastaId));
    }

    @Transactional(readOnly = true)
    public Estado estado(UUID subastaId) {
        boolean activa = transmisiones.findById(subastaId).map(Transmision::isActiva).orElse(false);
        return new Estado(activa, video.nombreDeSala(subastaId));
    }

    /** Credenciales de solo lectura para quien quiere ver el video. Solo existen si hay transmisión activa. */
    @Transactional(readOnly = true)
    public Credenciales credencialesDeEspectador(UUID subastaId, UsuarioActual usuario) {
        if (!estado(subastaId).transmitiendo()) {
            throw ErrorDeNegocio.conflicto("La transmisión aún no ha iniciado");
        }
        return video.credencialesDeEspectador(subastaId, usuario.id(), usuario.nombre());
    }

    /**
     * Eventos que el proveedor de video envía por webhook.
     * <ul>
     *   <li>Hallazgo 12: si el Subastador cierra la pestaña o pierde la conexión, LiveKit avisa que salió
     *       y la transmisión se detiene desde el servidor.</li>
     *   <li>Hallazgo 16: si alguien publica video sin una transmisión activa suya (por ejemplo, con un token
     *       viejo tras detener), se le saca de la sala.</li>
     * </ul>
     */
    @Transactional
    public void alEventoDeSala(EventoDeSala evento) {
        Transmision t = transmisiones.findById(evento.subastaId()).orElse(null);
        switch (evento.tipo()) {
            case PISTA_PUBLICADA -> {
                boolean autorizado = t != null && t.isActiva() && evento.participanteId() != null
                        && t.getSubastadorId().toString().equals(evento.participanteId());
                if (autorizado) {
                    t.registrarEmisor(evento.participanteSid());
                    transmisiones.save(t);
                } else if (evento.participanteId() != null) {
                    log.warn("Se expulsa a {} de la sala de la subasta {}: publicó video sin transmisión activa",
                            evento.participanteId(), evento.subastaId());
                    despuesDeConfirmar(() -> video.expulsar(evento.subastaId(), evento.participanteId()));
                }
            }
            case PISTA_RETIRADA -> {
                if (evento.pistaDeVideo() && t != null && t.esElEmisor(evento.participanteSid())) {
                    detenerYAvisar(t);
                }
            }
            case PARTICIPANTE_SALIO -> {
                if (t != null && t.esElEmisor(evento.participanteSid())) {
                    detenerYAvisar(t);
                }
            }
            case SALA_CERRADA -> {
                if (t != null && t.isActiva()) {
                    detenerYAvisar(t);
                }
            }
            case OTRO -> {
                // Nada que hacer.
            }
        }
    }

    /** Detiene, guarda el evento en la outbox y, cuando se confirma la transacción, cierra la sala de video. */
    private void detenerYAvisar(Transmision t) {
        UUID subastaId = t.getSubastaId();
        t.detener(reloj.instant());
        transmisiones.save(t);
        eventos.registrar(Eventos.TRANSMISION_DETENIDA, new TransmisionDetenida(subastaId));
        // Hallazgo 16: al cerrar la sala se desconecta a todos y el token del emisor deja de servir para esta sesión.
        despuesDeConfirmar(() -> video.cerrarSala(subastaId));
    }

    private void exigirDueno(Transmision t, UsuarioActual usuario) {
        if (!t.getSubastadorId().equals(usuario.id())) {
            throw ErrorDeNegocio.prohibido("La transmisión pertenece a otro Subastador");
        }
    }

    /** Las llamadas a LiveKit se hacen fuera de la transacción, y solo si esta se confirmó. */
    private static void despuesDeConfirmar(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
