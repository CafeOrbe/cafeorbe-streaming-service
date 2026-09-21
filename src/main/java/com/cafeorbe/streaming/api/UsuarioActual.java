package com.cafeorbe.streaming.api;

import com.cafeorbe.contracts.Rol;

import java.util.UUID;

/** Identidad del usuario, tomada de las cabeceras que inyecta el api-gateway. */
public record UsuarioActual(UUID id, String nombre, Rol rol) {

    public void exigirSubastador() {
        if (rol != Rol.SUBASTADOR) {
            throw ErrorDeNegocio.prohibido("No autorizado");
        }
    }
}
