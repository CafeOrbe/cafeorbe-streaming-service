package com.cafeorbe.streaming.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cafeorbe.outbox.scheduler", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxPublisher publicador;

    public OutboxScheduler(OutboxPublisher publicador) {
        this.publicador = publicador;
    }

    @Scheduled(fixedDelayString = "${cafeorbe.outbox.intervalo-ms:200}")
    public void publicar() {
        publicador.publicarPendientes();
    }
}
