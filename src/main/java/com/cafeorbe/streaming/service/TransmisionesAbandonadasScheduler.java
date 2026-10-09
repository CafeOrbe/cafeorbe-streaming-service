package com.cafeorbe.streaming.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Revisa cada pocos segundos si algún emisor ausente agotó su periodo de gracia. Se puede desactivar en las pruebas. */
@Component
@ConditionalOnProperty(name = "cafeorbe.transmision.scheduler", havingValue = "true", matchIfMissing = true)
public class TransmisionesAbandonadasScheduler {

    private final TransmisionService transmisiones;
    private final Clock reloj;

    public TransmisionesAbandonadasScheduler(TransmisionService transmisiones, Clock reloj) {
        this.transmisiones = transmisiones;
        this.reloj = reloj;
    }

    @Scheduled(fixedDelayString = "${cafeorbe.transmision.intervalo-ms:5000}")
    public void detener() {
        transmisiones.detenerAbandonadas(reloj.instant());
    }
}
