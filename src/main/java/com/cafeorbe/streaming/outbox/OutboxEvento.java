package com.cafeorbe.streaming.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Evento pendiente de publicar. Mismo patrón outbox que el auction-service (hallazgo 14). */
@Entity
@Table(name = "outbox")
public class OutboxEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false, length = 60)
    private String tipo;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    protected OutboxEvento() {
    }

    public OutboxEvento(UUID eventId, String tipo, String payload, Instant creadoEn) {
        this.eventId = eventId;
        this.tipo = tipo;
        this.payload = payload;
        this.creadoEn = creadoEn;
    }

    public void marcarPublicado(Instant cuando) {
        this.publicadoEn = cuando;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }
}
