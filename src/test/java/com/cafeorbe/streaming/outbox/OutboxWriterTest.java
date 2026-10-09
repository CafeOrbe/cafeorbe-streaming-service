package com.cafeorbe.streaming.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxWriterTest {

    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("registrar(): guarda el evento serializado con su tipo")
    void guardaElEvento() {
        OutboxRepository repositorio = mock(OutboxRepository.class);

        new OutboxWriter(repositorio, new ObjectMapper().findAndRegisterModules(), reloj)
                .registrar("transmision.iniciada", Map.of("sala", "subasta-1"));

        ArgumentCaptor<OutboxEvento> guardado = ArgumentCaptor.forClass(OutboxEvento.class);
        verify(repositorio).save(guardado.capture());
        OutboxEvento evento = guardado.getValue();
        assertThat(evento.getTipo()).isEqualTo("transmision.iniciada");
        assertThat(evento.getEventId()).isNotNull();
        assertThat(evento.getId()).isNull();
        assertThat(evento.getPublicadoEn()).isNull();
        assertThat(evento.getPayload()).contains("subasta-1");
    }

    @Test
    @DisplayName("registrar(): si el evento no se puede serializar falla y no guarda nada")
    void fallaSiNoSePuedeSerializar() throws JsonProcessingException {
        OutboxRepository repositorio = mock(OutboxRepository.class);
        ObjectMapper json = mock(ObjectMapper.class);
        when(json.writeValueAsString(any())).thenThrow(new JsonProcessingException("roto") { });

        OutboxWriter escritor = new OutboxWriter(repositorio, json, reloj);

        assertThatThrownBy(() -> escritor.registrar("transmision.iniciada", "datos"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transmision.iniciada");
        verify(repositorio, never()).save(any());
    }
}
