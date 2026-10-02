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
 * Webhooks de LiveKit (hallazgos 12 y 16). No hay usuario ni token de sesión: la petición se acepta solo si
 * viene firmada con la clave de la API de LiveKit. Tiene dos rutas: la interna, a la que LiveKit llama directo
 * en local, y la que el api-gateway deja pasar sin token, para LiveKit Cloud cuando este servicio no es público.
 */
@RestController
public class LiveKitWebhookController {

    private final ProveedorDeVideo video;
    private final TransmisionService transmisiones;

    public LiveKitWebhookController(ProveedorDeVideo video, TransmisionService transmisiones) {
        this.video = video;
        this.transmisiones = transmisiones;
    }

    @PostMapping({"/internal/livekit/webhook", "/api/streaming/webhooks/livekit"})
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
