package com.cafeorbe.streaming.outbox;

import com.cafeorbe.contracts.EventoEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Guarda el evento en la tabla outbox dentro de la misma transacción que cambia el estado de la transmisión.
 * Así el cambio y su evento se guardan juntos o no se guarda ninguno, aunque RabbitMQ esté caído.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final Clock reloj;

    public OutboxWriter(OutboxRepository outbox, ObjectMapper json, Clock reloj) {
        this.outbox = outbox;
        this.json = json;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String tipo, Object datos) {
        var envoltorio = new EventoEnvelope<>(UUID.randomUUID(), tipo, 1, reloj.instant(), datos);
        try {
            outbox.save(new OutboxEvento(envoltorio.eventId(), tipo, json.writeValueAsString(envoltorio),
                    envoltorio.ocurridoEn()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + tipo, e);
        }
    }
}
