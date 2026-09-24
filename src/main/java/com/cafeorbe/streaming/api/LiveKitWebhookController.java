package com.cafeorbe.streaming.api;

import com.cafeorbe.streaming.provider.ProveedorDeVideo;
import com.cafeorbe.streaming.provider.ProveedorDeVideo.WebhookInvalidoException;
import com.cafeorbe.streaming.service.TransmisionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhooks de LiveKit (hallazgos 12 y 16). LiveKit llama directo a este servicio, no pasa por el api-gateway,
 * así que no hay usuario: la petición se acepta solo si viene firmada con la clave de la API de LiveKit.
 */
@RestController
public class LiveKitWebhookController {

    private final ProveedorDeVideo video;
    private final TransmisionService transmisiones;

    public LiveKitWebhookController(ProveedorDeVideo video, TransmisionService transmisiones) {
        this.video = video;
        this.transmisiones = transmisiones;
    }

    @PostMapping("/internal/livekit/webhook")
    public ResponseEntity<Void> recibir(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String firma,
                                        @RequestBody(required = false) byte[] cuerpo) {
        try {
            video.leerWebhook(firma, cuerpo).ifPresent(transmisiones::alEventoDeSala);
        } catch (WebhookInvalidoException e) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok().build();
    }
}
