package com.cafeorbe.streaming.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación para LiveKit. Un token de acceso de LiveKit es un JWT HS256 firmado con el secreto
 * de la API, con la sala y los permisos dentro del claim {@code video}; no hace falta su SDK de servidor.
 * Con el mismo tipo de token se llama a su API de servidor (Twirp sobre HTTP) y se validan sus webhooks.
 */
@Component
public class LiveKitProveedor implements ProveedorDeVideo {

    private static final Logger log = LoggerFactory.getLogger(LiveKitProveedor.class);
    private static final String PREFIJO_SALA = "subasta-";

    private final String url;
    private final String apiKey;
    private final SecretKey clave;
    private final Duration vida;
    private final RestClient api;
    private final ObjectMapper json;

    public LiveKitProveedor(@Value("${cafeorbe.livekit.url}") String url,
                            @Value("${cafeorbe.livekit.api-url}") String apiUrl,
                            @Value("${cafeorbe.livekit.api-key}") String apiKey,
                            @Value("${cafeorbe.livekit.api-secret}") String apiSecret,
                            @Value("${cafeorbe.livekit.minutos-de-vida-del-token}") long minutos,
                            ObjectMapper json) {
        this.url = url;
        this.apiKey = apiKey;
        this.clave = Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
        this.vida = Duration.ofMinutes(minutos);
        this.json = json;
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(1000);
        fabrica.setReadTimeout(3000);
        this.api = RestClient.builder().baseUrl(apiUrl).requestFactory(fabrica).build();
    }

    @Override
    public String nombreDeSala(UUID subastaId) {
        return PREFIJO_SALA + subastaId;
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
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
        return new Credenciales(url, token, sala);
    }

    // ── API de servidor (hallazgo 16) ─────────────────────────────────────

    @Override
    public void cerrarSala(UUID subastaId) {
        String sala = nombreDeSala(subastaId);
        llamarRoomService("DeleteRoom", Map.of("room", sala), Map.of("roomCreate", true));
    }

    @Override
    public void expulsar(UUID subastaId, String participanteId) {
        String sala = nombreDeSala(subastaId);
        llamarRoomService("RemoveParticipant", Map.of("room", sala, "identity", participanteId),
                Map.of("room", sala, "roomAdmin", true));
    }

    private void llamarRoomService(String metodo, Map<String, Object> cuerpo, Map<String, Object> permisos) {
        Instant ahora = Instant.now();
        String token = Jwts.builder()
                .issuer(apiKey)
                .subject("cafeorbe-streaming-service")
                .claim("video", permisos)
                .notBefore(Date.from(ahora.minusSeconds(10)))
                .expiration(Date.from(ahora.plusSeconds(60)))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
        try {
            api.post().uri("/twirp/livekit.RoomService/{metodo}", metodo)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // No bloquea al usuario: la transmisión ya quedó detenida en la base de datos.
            log.warn("LiveKit {} falló para {}: {}", metodo, cuerpo, e.getMessage());
        }
    }

    // ── Webhooks (hallazgos 12 y 16) ──────────────────────────────────────

    @Override
    public Optional<EventoDeSala> leerWebhook(String autorizacion, byte[] cuerpo) {
        verificarFirma(autorizacion, cuerpo);
        JsonNode evento;
        try {
            evento = json.readTree(cuerpo);
        } catch (IOException e) {
            throw new WebhookInvalidoException("El cuerpo del webhook no es JSON");
        }

        String sala = evento.path("room").path("name").asText("");
        if (!sala.startsWith(PREFIJO_SALA)) {
            return Optional.empty();
        }
        UUID subastaId;
        try {
            subastaId = UUID.fromString(sala.substring(PREFIJO_SALA.length()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        TipoEventoDeSala tipo = switch (evento.path("event").asText("")) {
            case "track_published" -> TipoEventoDeSala.PISTA_PUBLICADA;
            case "track_unpublished" -> TipoEventoDeSala.PISTA_RETIRADA;
            case "participant_left" -> TipoEventoDeSala.PARTICIPANTE_SALIO;
            case "room_finished" -> TipoEventoDeSala.SALA_CERRADA;
            default -> TipoEventoDeSala.OTRO;
        };
        JsonNode participante = evento.path("participant");
        String tipoDePista = evento.path("track").path("type").asText("");
        return Optional.of(new EventoDeSala(tipo, subastaId, texto(participante.path("identity")),
                texto(participante.path("sid")), "VIDEO".equals(tipoDePista) || "1".equals(tipoDePista)));
    }

    /**
     * LiveKit firma cada webhook con un JWT (cabecera Authorization) emitido con la clave de la API,
     * cuyo claim {@code sha256} es el hash del cuerpo en Base64.
     */
    private void verificarFirma(String autorizacion, byte[] cuerpo) {
        if (autorizacion == null || autorizacion.isBlank() || cuerpo == null) {
            throw new WebhookInvalidoException("Webhook sin firma");
        }
        String token = autorizacion.startsWith("Bearer ") ? autorizacion.substring(7) : autorizacion;
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(clave).requireIssuer(apiKey).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new WebhookInvalidoException("Firma del webhook inválida");
        }
        String esperado = claims.get("sha256", String.class);
        String calculado = Base64.getEncoder().encodeToString(sha256(cuerpo));
        if (esperado == null || !MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                calculado.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookInvalidoException("El cuerpo del webhook no coincide con su firma");
        }
    }

    private static byte[] sha256(byte[] datos) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(datos);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String texto(JsonNode nodo) {
        return nodo.isMissingNode() || nodo.isNull() || nodo.asText().isEmpty() ? null : nodo.asText();
    }
}
