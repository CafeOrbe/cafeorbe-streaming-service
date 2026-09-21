package com.cafeorbe.streaming.provider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Implementación para LiveKit. Un token de acceso de LiveKit es un JWT HS256 firmado con el secreto
 * de la API, con la sala y los permisos dentro del claim {@code video}; no hace falta su SDK de servidor.
 */
@Component
public class LiveKitProveedor implements ProveedorDeVideo {

    private final String url;
    private final String apiKey;
    private final SecretKey clave;
    private final Duration vida;

    public LiveKitProveedor(@Value("${cafeorbe.livekit.url}") String url,
                            @Value("${cafeorbe.livekit.api-key}") String apiKey,
                            @Value("${cafeorbe.livekit.api-secret}") String apiSecret,
                            @Value("${cafeorbe.livekit.minutos-de-vida-del-token}") long minutos) {
        this.url = url;
        this.apiKey = apiKey;
        this.clave = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
        this.vida = Duration.ofMinutes(minutos);
    }

    @Override
    public String nombreDeSala(UUID subastaId) {
        return "subasta-" + subastaId;
    }

    @Override
    public Credenciales credencialesDeEmisor(UUID subastaId, UUID usuarioId, String nombre) {
        return credenciales(subastaId, usuarioId, nombre, true);
    }

    @Override
    public Credenciales credencialesDeEspectador(UUID subastaId, UUID usuarioId, String nombre) {
        return credenciales(subastaId, usuarioId, nombre, false);
    }

    private Credenciales credenciales(UUID subastaId, UUID usuarioId, String nombre, boolean emisor) {
        String sala = nombreDeSala(subastaId);
        Instant ahora = Instant.now();
        Map<String, Object> permisos = Map.of(
                "room", sala,
                "roomJoin", true,
                "canPublish", emisor,
                "canPublishData", false,
                "canSubscribe", true);
        String token = Jwts.builder()
                .issuer(apiKey)
                .subject(usuarioId.toString())
                .claim("name", nombre)
                .claim("video", permisos)
                .notBefore(Date.from(ahora.minusSeconds(10)))
                .expiration(Date.from(ahora.plus(vida)))
                .signWith(clave)
                .compact();
        return new Credenciales(url, token, sala);
    }
}
