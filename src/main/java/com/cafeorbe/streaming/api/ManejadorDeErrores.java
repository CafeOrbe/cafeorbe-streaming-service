package com.cafeorbe.streaming.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class ManejadorDeErrores {

    public record ApiError(int status, String mensaje, Map<String, String> campos) {
    }

    @ExceptionHandler(ErrorDeNegocio.class)
    public ResponseEntity<ApiError> negocio(ErrorDeNegocio e) {
        return ResponseEntity.status(e.getEstado())
                .body(new ApiError(e.getEstado().value(), e.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> solicitudInvalida(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ApiError(400, "La solicitud no es válida", Map.of()));
    }
}
