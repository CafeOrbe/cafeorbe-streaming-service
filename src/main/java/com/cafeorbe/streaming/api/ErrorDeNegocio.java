package com.cafeorbe.streaming.api;

import org.springframework.http.HttpStatus;

public class ErrorDeNegocio extends RuntimeException {

    private final HttpStatus estado;

    private ErrorDeNegocio(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public static ErrorDeNegocio prohibido(String mensaje) {
        return new ErrorDeNegocio(HttpStatus.FORBIDDEN, mensaje);
    }

    public static ErrorDeNegocio conflicto(String mensaje) {
        return new ErrorDeNegocio(HttpStatus.CONFLICT, mensaje);
    }

    public static ErrorDeNegocio noEncontrado(String mensaje) {
        return new ErrorDeNegocio(HttpStatus.NOT_FOUND, mensaje);
    }

    /** Un servicio del que dependemos (auction) no respondió. */
    public static ErrorDeNegocio noDisponible(String mensaje) {
        return new ErrorDeNegocio(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
    }

    public static ErrorDeNegocio sinSesion() {
        return new ErrorDeNegocio(HttpStatus.UNAUTHORIZED, "Sesión requerida");
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
