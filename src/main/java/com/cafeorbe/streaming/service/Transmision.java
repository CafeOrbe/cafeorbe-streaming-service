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

    @Column(name = "subastador_id", nullable = false)
    private UUID subastadorId;

    @Column(nullable = false)
    private boolean activa;

    @Column(name = "iniciada_en")
    private Instant iniciadaEn;

    @Column(name = "detenida_en")
    private Instant detenidaEn;

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
    }

    public void detener(Instant ahora) {
        this.activa = false;
        this.detenidaEn = ahora;
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
}
