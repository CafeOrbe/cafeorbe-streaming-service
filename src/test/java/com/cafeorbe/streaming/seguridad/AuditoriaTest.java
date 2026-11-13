package com.cafeorbe.streaming.seguridad;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuditoriaTest {

    @Test
    @DisplayName("SEG-02 · La IP auditada es la del cliente: la primera de X-Forwarded-For (ingress y api-gateway)")
    void ipDelClienteTrasElProxy() {
        var peticion = new MockHttpServletRequest("GET", "/api/streaming/x");
        peticion.setRemoteAddr("10.0.0.5");
        peticion.addHeader("X-Forwarded-For", " 203.0.113.7 , 10.0.0.9");

        assertThat(Auditoria.ipDe(peticion)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("SEG-02 · Sin X-Forwarded-For (o vacía) se audita la IP del socket")
    void ipDelSocketSinProxy() {
        var peticion = new MockHttpServletRequest("GET", "/api/streaming/x");
        peticion.setRemoteAddr("10.0.0.5");
        assertThat(Auditoria.ipDe(peticion)).isEqualTo("10.0.0.5");

        peticion.addHeader("X-Forwarded-For", "  ");
        assertThat(Auditoria.ipDe(peticion)).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("SEG-02 · Cada rechazo suma la métrica por evento y motivo")
    void rechazoSumaLaMetrica() {
        var metricas = new SimpleMeterRegistry();
        var peticion = new MockHttpServletRequest("GET", "/api/streaming/x");
        peticion.addHeader("X-Forwarded-For", "203.0.113.7");

        new Auditoria(metricas).rechazo("acceso_interno_rechazado", "sin_credencial", null, peticion);

        assertThat(metricas.counter("cafeorbe.seguridad.eventos", "evento", "acceso_interno_rechazado",
                "motivo", "sin_credencial").count()).isEqualTo(1);
    }
}
