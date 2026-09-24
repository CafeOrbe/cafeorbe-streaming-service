package com.cafeorbe.streaming.outbox;

import com.cafeorbe.contracts.Eventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Publica en el broker los eventos pendientes de la outbox. Entrega al-menos-una-vez:
 * si el broker falla, el evento sigue pendiente y se reintenta en la siguiente pasada.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int TAMANO_LOTE = 50;

    private final OutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final Clock reloj;

    public OutboxPublisher(OutboxRepository outbox, RabbitTemplate rabbit, Clock reloj) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.reloj = reloj;
    }

    /** @return cantidad de eventos publicados en esta pasada */
    @Transactional
    public int publicarPendientes() {
        int publicados = 0;
        for (OutboxEvento evento : outbox.pendientes(PageRequest.of(0, TAMANO_LOTE))) {
            try {
                Message mensaje = MessageBuilder
                        .withBody(evento.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setMessageId(evento.getEventId().toString())
                        .build();
                rabbit.send(Eventos.EXCHANGE, evento.getTipo(), mensaje);
                evento.marcarPublicado(reloj.instant());
                publicados++;
            } catch (RuntimeException e) {
                // Se conserva el orden: no se publican eventos posteriores si este falló.
                log.warn("No se pudo publicar el evento {} ({}); se reintentará: {}", evento.getEventId(),
                        evento.getTipo(), e.getMessage());
                break;
            }
        }
        return publicados;
    }
}
