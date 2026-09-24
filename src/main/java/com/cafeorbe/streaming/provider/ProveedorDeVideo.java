package com.cafeorbe.streaming.provider;

import java.util.Optional;
import java.util.UUID;

/**
 * Único punto de contacto con el proveedor de video WebRTC. El resto del servicio no conoce su SDK,
 * así que cambiar de proveedor implica escribir otra implementación de esta interfaz.
 */
public interface ProveedorDeVideo {

    /** Credenciales que el navegador usa para conectarse a la sala de video. */
    record Credenciales(String url, String token, String sala) {
    }

    /** Lo que pasó en una sala de video, traducido del webhook del proveedor. */
    enum TipoEventoDeSala {
        PISTA_PUBLICADA,
        PISTA_RETIRADA,
        PARTICIPANTE_SALIO,
        SALA_CERRADA,
        OTRO
    }

    /**
     * @param participanteId  identidad del participante (el id del usuario de CaféOrbe), o {@code null}
     * @param participanteSid id de la sesión del participante en el proveedor, o {@code null}
     * @param pistaDeVideo    {@code true} si el evento es sobre una pista de video
     */
    record EventoDeSala(TipoEventoDeSala tipo, UUID subastaId, String participanteId, String participanteSid,
                        boolean pistaDeVideo) {
    }

    /** El webhook no viene firmado por el proveedor (o la firma no coincide con el cuerpo). */
    class WebhookInvalidoException extends RuntimeException {
        public WebhookInvalidoException(String mensaje) {
            super(mensaje);
        }
    }

    String nombreDeSala(UUID subastaId);

    /** Credenciales con permiso para publicar video (el Subastador). */
    Credenciales credencialesDeEmisor(UUID subastaId, UUID usuarioId, String nombre);

    /** Credenciales solo de lectura (los compradores). */
    Credenciales credencialesDeEspectador(UUID subastaId, UUID usuarioId, String nombre);

    /** Cierra la sala y desconecta a todos (hallazgo 16). Si el proveedor no responde, solo se registra en el log. */
    void cerrarSala(UUID subastaId);

    /** Saca a un participante de la sala (hallazgo 16). Si el proveedor no responde, solo se registra en el log. */
    void expulsar(UUID subastaId, String participanteId);

    /**
     * Valida la firma del webhook y lo traduce. Vacío si el evento no es de una sala de subasta.
     *
     * @throws WebhookInvalidoException si la firma falta o no es válida
     */
    Optional<EventoDeSala> leerWebhook(String autorizacion, byte[] cuerpo);
}
