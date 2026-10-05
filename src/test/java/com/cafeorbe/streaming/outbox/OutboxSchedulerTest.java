package com.cafeorbe.streaming.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * El scheduler es lo que saca los eventos de la outbox hacia RabbitMQ. Si deja de correr, los eventos se
 * acumulan en la base y las salas no se enteran: por eso se prueba que cada pasada publica lo pendiente.
 */
class OutboxSchedulerTest {

    @Test
    @DisplayName("publicar(): delega en el publicador, que es quien decide cuántos eventos salen")
    void delegaEnElPublicador() {
        OutboxPublisher publicador = mock(OutboxPublisher.class);

        new OutboxScheduler(publicador).publicar();

        verify(publicador).publicarPendientes();
    }

    @Test
    @DisplayName("publicar(): en cada pasada se intenta publicar, sin saltarse ni acumular trabajo")
    void publicaEnCadaPasada() {
        OutboxPublisher publicador = mock(OutboxPublisher.class);
        var scheduler = new OutboxScheduler(publicador);

        scheduler.publicar();
        scheduler.publicar();
        scheduler.publicar();

        verify(publicador, times(3)).publicarPendientes();
    }

    @Test
    @DisplayName("publicar(): una pasada que falla no impide la siguiente; el evento se reintenta")
    void unaPasadaFallidaNoDejaElSchedulerMuerto() {
        OutboxPublisher publicador = mock(OutboxPublisher.class);
        doThrow(new RuntimeException("RabbitMQ no disponible"))
                .doReturn(0)
                .when(publicador).publicarPendientes();
        var scheduler = new OutboxScheduler(publicador);

        assertThatThrownBy(scheduler::publicar).isInstanceOf(RuntimeException.class);

        assertThatCode(scheduler::publicar).doesNotThrowAnyException();
        verify(publicador, times(2)).publicarPendientes();
    }
}
