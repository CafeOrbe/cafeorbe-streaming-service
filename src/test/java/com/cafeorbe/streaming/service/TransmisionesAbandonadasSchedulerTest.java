package com.cafeorbe.streaming.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransmisionesAbandonadasSchedulerTest {

    @Test
    @DisplayName("detener(): cada pasada detiene las transmisiones abandonadas a la hora actual del reloj")
    void detieneConLaHoraDelReloj() {
        TransmisionService transmisiones = mock(TransmisionService.class);
        Instant ahora = Instant.parse("2026-10-08T12:00:00Z");

        new TransmisionesAbandonadasScheduler(transmisiones, Clock.fixed(ahora, ZoneOffset.UTC)).detener();

        verify(transmisiones).detenerAbandonadas(ahora);
    }
}
