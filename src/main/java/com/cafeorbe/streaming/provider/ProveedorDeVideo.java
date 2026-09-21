package com.cafeorbe.streaming.provider;

import java.util.UUID;

/**
 * Único punto de contacto con el proveedor de video WebRTC. El resto del servicio no conoce su SDK,
 * así que cambiar de proveedor implica escribir otra implementación de esta interfaz.
 */
public interface ProveedorDeVideo {

    /** Credenciales que el navegador usa para conectarse a la sala de video. */
    record Credenciales(String url, String token, String sala) {
    }

    String nombreDeSala(UUID subastaId);

    /** Credenciales con permiso para publicar video (el Subastador). */
    Credenciales credencialesDeEmisor(UUID subastaId, UUID usuarioId, String nombre);

    /** Credenciales solo de lectura (los compradores). */
    Credenciales credencialesDeEspectador(UUID subastaId, UUID usuarioId, String nombre);
}
