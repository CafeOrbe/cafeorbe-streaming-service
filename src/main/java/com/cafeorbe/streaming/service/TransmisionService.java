package com.cafeorbe.streaming.service;

import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.streaming.api.ErrorDeNegocio;
import com.cafeorbe.streaming.api.UsuarioActual;
import com.cafeorbe.streaming.provider.ProveedorDeVideo;
import com.cafeorbe.streaming.provider.ProveedorDeVideo.Credenciales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

import com.cafeorbe.contracts.eventos.TransmisionDetenida;
import com.cafeorbe.contracts.eventos.TransmisionIniciada;

/** HU-11: iniciar y detener la transmisión, y entregar credenciales de la sala de video. */
@Service
public class TransmisionService {

    private static final Logger log = LoggerFactory.getLogger(TransmisionService.class);

    public record Estado(boolean transmitiendo, String sala) {
    }

    private final TransmisionRepository transmisiones;
    private final ProveedorDeVideo video;
    private final RabbitTemplate rabbit;
    private final Clock reloj;

    public TransmisionService(TransmisionRepository transmisiones, ProveedorDeVideo video, RabbitTemplate rabbit,
                              Clock reloj) {
        this.transmisiones = transmisiones;
        this.video = video;
        this.rabbit = rabbit;
        this.reloj = reloj;
    }

    /** Marca la transmisión como activa y devuelve credenciales de emisor. Idempotente si ya estaba activa. */
    @Transactional
    public Credenciales iniciar(UUID subastaId, UsuarioActual usuario) {
        usuario.exigirSubastador();
        Transmision t = transmisiones.findById(subastaId).orElseGet(() -> new Transmision(subastaId, usuario.id()));
        exigirDueno(t, usuario);
        boolean cambio = !t.isActiva();
        t.iniciar(usuario.id(), reloj.instant());
        transmisiones.save(t);
        if (cambio) {
            publicar(Eventos.TRANSMISION_INICIADA, new TransmisionIniciada(subastaId, video.nombreDeSala(subastaId)));
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
        t.detener(reloj.instant());
        transmisiones.save(t);
        publicar(Eventos.TRANSMISION_DETENIDA, new TransmisionDetenida(subastaId));
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

    private void exigirDueno(Transmision t, UsuarioActual usuario) {
        if (t.isActiva() && !t.getSubastadorId().equals(usuario.id())) {
            throw ErrorDeNegocio.prohibido("La transmisión pertenece a otro Subastador");
        }
    }

    private void publicar(String tipo, Object datos) {
        try {
            rabbit.convertAndSend(Eventos.EXCHANGE, tipo, new EventoEnvelope<>(UUID.randomUUID(), tipo, 1,
                    reloj.instant(), datos));
        } catch (RuntimeException e) {
            // El estado ya quedó guardado; los compradores lo ven al consultar /estado al entrar a la sala.
            log.warn("No se pudo publicar {}: {}", tipo, e.getMessage());
        }
    }
}
