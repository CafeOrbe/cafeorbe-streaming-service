package com.cafeorbe.streaming.service;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /** Desde cuándo falta el video del emisor; {@code null} mientras publica con normalidad. */
    @Column(name = "emisor_ausente_desde")
    private Instant emisorAusenteDesde;

    /**
     * Sala de LiveKit (su SID) en la que publica esta transmisión. Si la transmisión se detiene y se vuelve a
     * iniciar, LiveKit crea una sala nueva con el mismo nombre y otro SID: así un webhook tardío de la sala
     * anterior no detiene la transmisión nueva.
     */
    @Column(name = "sala_sid", length = 64)
    private String salaSid;

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
        this.emisorAusenteDesde = null;
        this.salaSid = null;
    }

    /**
     * Recién iniciada todavía no llega video. Se cuenta como ausencia desde {@code desde}: si el emisor nunca
     * llega a publicar (cerró la pestaña, negó la cámara), la transmisión no queda activa para siempre.
     * Se cancela con {@link #registrarEmisor} en cuanto llega su video.
     */
    public void esperarPrimerVideo(Instant desde) {
        if (emisorSid == null) {
            this.emisorAusenteDesde = desde;
        }
    }

    public void detener(Instant ahora) {
        this.activa = false;
        this.detenidaEn = ahora;
        this.emisorSid = null;
        this.emisorAusenteDesde = null;
    }

    public void registrarEmisor(String sid, String salaSid) {
        this.emisorSid = sid;
        this.emisorAusenteDesde = null;
        if (salaSid != null) {
            this.salaSid = salaSid;
        }
    }

    /**
     * ¿El cierre de sala que avisa LiveKit es de esta transmisión? Si se conoce el SID de la sala, debe coincidir;
     * si aún no llegó video (sin SID), solo cuenta un evento generado después de iniciar.
     */
    public boolean esDeEstaTransmision(String salaSidDelEvento, Instant creadoEn) {
        if (!activa) {
            return false;
        }
        if (salaSid != null && salaSidDelEvento != null) {
            return salaSid.equals(salaSidDelEvento);
        }
        if (creadoEn != null && iniciadaEn != null) {
            // createdAt de LiveKit viene en segundos: se compara al segundo para no descartar el evento propio.
            return !creadoEn.isBefore(iniciadaEn.truncatedTo(ChronoUnit.SECONDS));
        }
        // Sin datos para distinguirla (proveedor que no envía SID ni fecha): se trata como de esta transmisión.
        return true;
    }

    /** El video del emisor dejó de llegar. Si ya estaba ausente se conserva el primer instante. */
    public void marcarEmisorAusente(Instant ahora) {
        if (emisorAusenteDesde == null) {
            emisorAusenteDesde = ahora;
        }
    }

    /** El Subastador volvió a pedir credenciales: está reconectando, el plazo de ausencia empieza de nuevo. */
    public void emisorReconectando(Instant ahora) {
        if (emisorAusenteDesde != null) {
            emisorAusenteDesde = ahora;
        }
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

    public Instant getEmisorAusenteDesde() {
        return emisorAusenteDesde;
    }

    public String getSalaSid() {
        return salaSid;
    }
}
