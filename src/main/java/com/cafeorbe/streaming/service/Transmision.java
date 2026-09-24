package com.cafeorbe.streaming.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transmision")
public class Transmision {

    @Id
    @Column(name = "subasta_id")
    private UUID subastaId;

    /** Dueño de la subasta según el auction-service (hallazgo 11): el único que puede transmitir. */
    @Column(name = "subastador_id", nullable = false)
    private UUID subastadorId;

    @Column(nullable = false)
    private boolean activa;

    @Column(name = "iniciada_en")
    private Instant iniciadaEn;

    @Column(name = "detenida_en")
    private Instant detenidaEn;

    /** Sesión de LiveKit que está publicando el video; sirve para reconocer su salida en los webhooks. */
    @Column(name = "emisor_sid", length = 64)
    private String emisorSid;

    protected Transmision() {
    }

    public Transmision(UUID subastaId, UUID subastadorId) {
        this.subastaId = subastaId;
        this.subastadorId = subastadorId;
    }

    public void iniciar(UUID subastadorId, Instant ahora) {
        this.subastadorId = subastadorId;
        this.activa = true;
        this.iniciadaEn = ahora;
        this.detenidaEn = null;
        this.emisorSid = null;
    }

    public void detener(Instant ahora) {
        this.activa = false;
        this.detenidaEn = ahora;
        this.emisorSid = null;
    }

    public void registrarEmisor(String sid) {
        this.emisorSid = sid;
    }

    /** ¿Esta sesión de LiveKit es la que está emitiendo el video ahora mismo? */
    public boolean esElEmisor(String sid) {
        return activa && emisorSid != null && emisorSid.equals(sid);
    }

    public UUID getSubastaId() {
        return subastaId;
    }

    public UUID getSubastadorId() {
        return subastadorId;
    }

    public boolean isActiva() {
        return activa;
    }

    public String getEmisorSid() {
        return emisorSid;
    }
}
