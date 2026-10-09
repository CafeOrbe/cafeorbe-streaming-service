package com.cafeorbe.streaming.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Esta clase es la frontera con el proveedor de video: emite los tokens con los que el navegador entra a la
 * sala, valida la firma de cada webhook que entra y llama a la API de servidor para cerrar salas y expulsar.
 * La firma se prueba de verdad (JWT firmado con el mismo secreto) porque no basta con comprobar que hay algo.
 */
class LiveKitProveedorTest {

    static final String API_KEY = "cafeorbe-test";
    static final String API_SECRET = "cafeorbe-livekit-dev-secret-cambiar-en-prod-0123456789";
    static final UUID SUBASTA = UUID.randomUUID();
    static final UUID USUARIO = UUID.randomUUID();

    static final SecretKey CLAVE = Keys.hmacShaKeyFor(API_SECRET.getBytes(StandardCharsets.UTF_8));

    private static HttpServer livekit;
    private static int puertoCaido;
    private static final AtomicInteger codigo = new AtomicInteger(200);
    private static final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private static final AtomicReference<String> autorizacionRecibida = new AtomicReference<>();
    private static final AtomicReference<String> rutaRecibida = new AtomicReference<>();

    @BeforeAll
    static void levantarLiveKitFalso() throws IOException {
        livekit = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        livekit.createContext("/", intercambio -> {
            rutaRecibida.set(intercambio.getRequestURI().getPath());
            autorizacionRecibida.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(codigo.get(), bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        livekit.start();
        try (ServerSocket s = new ServerSocket(0)) {
            puertoCaido = s.getLocalPort();
        }
    }

    @AfterAll
    static void apagar() {
        livekit.stop(0);
    }

    private static String base() {
        return "http://localhost:" + livekit.getAddress().getPort();
    }

    /** api-url vacía: el proveedor la deduce del url público, que es lo que hace con LiveKit Cloud. */
    private static LiveKitProveedor proveedor() {
        return proveedor(base(), "");
    }

    private static LiveKitProveedor proveedor(String url, String apiUrl) {
        return new LiveKitProveedor(url, apiUrl, API_KEY, API_SECRET, 120, new ObjectMapper());
    }

    private static String sha256Base64(byte[] cuerpo) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(cuerpo));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Token de webhook tal como lo emite LiveKit: firmado con el secreto de la API y con el sha256 del cuerpo. */
    private static String firmaDe(byte[] cuerpo) {
        return Jwts.builder()
                .issuer(API_KEY)
                .claim("sha256", sha256Base64(cuerpo))
                .signWith(CLAVE, Jwts.SIG.HS256)
                .compact();
    }

    @Nested
    class Credenciales {

        @Test
        @DisplayName("El emisor puede publicar video; el espectador solo puede suscribirse")
        void permisosSegunElRol() {
            var emisor = proveedor().credencialesDeEmisor(SUBASTA, USUARIO, "Ana");
            var espectador = proveedor().credencialesDeEspectador(SUBASTA, USUARIO, "Ana");

            assertThat(permiso(emisor.token(), "canPublish")).isEqualTo(Boolean.TRUE);
            assertThat(permiso(espectador.token(), "canPublish")).isEqualTo(Boolean.FALSE);
            assertThat(permiso(emisor.token(), "canSubscribe")).isEqualTo(Boolean.TRUE);
            assertThat(permiso(emisor.token(), "canPublishData")).isEqualTo(Boolean.FALSE);
        }

        @Test
        @DisplayName("El token lleva la sala, el usuario y el nombre del emisor")
        void contenidoDelToken() {
            var emisor = proveedor().credencialesDeEmisor(SUBASTA, USUARIO, "José Ñandú");

            assertThat(emisor.sala()).isEqualTo("subasta-" + SUBASTA);
            assertThat(emisor.url()).isEqualTo(base());
            assertThat(permiso(emisor.token(), "room")).isEqualTo("subasta-" + SUBASTA);
            assertThat(sujeto(emisor.token())).isEqualTo(USUARIO.toString());
            assertThat(nombre(emisor.token())).isEqualTo("José Ñandú");
        }

        private Object permiso(String token, String claim) {
            return video(token).get(claim);
        }

        @SuppressWarnings("unchecked")
        private java.util.Map<String, Object> video(String token) {
            return claims(token).get("video", java.util.Map.class);
        }

        private io.jsonwebtoken.Claims claims(String token) {
            return Jwts.parser().verifyWith(CLAVE).build().parseSignedClaims(token).getPayload();
        }

        private String sujeto(String token) {
            return claims(token).getSubject();
        }

        private String nombre(String token) {
            return claims(token).get("name", String.class);
        }
    }

    @Nested
    class UrlDeLaApi {

        @Test
        @DisplayName("Con api-url vacía se deduce del url público cambiando ws por http")
        void deduceDelUrlPublico() {
            assertThat(LiveKitProveedor.urlDeLaApi("wss://cafeorbe.livekit.cloud", ""))
                    .isEqualTo("https://cafeorbe.livekit.cloud");
            assertThat(LiveKitProveedor.urlDeLaApi("ws://localhost:7880", null))
                    .isEqualTo("http://localhost:7880");
        }

        @Test
        @DisplayName("Si se indica api-url manda esa (el servicio puede alcanzar a LiveKit por otra dirección)")
        void respetaApiUrlIndicada() {
            assertThat(LiveKitProveedor.urlDeLaApi("wss://cafeorbe.livekit.cloud", "http://livekit:7880"))
                    .isEqualTo("http://livekit:7880");
        }
    }

    @Nested
    class FirmaDelWebhook {

        @Test
        @DisplayName("Un webhook sin cabecera de firma se rechaza")
        void sinFirma() {
            var cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
            var p = proveedor();

            assertThatThrownBy(() -> p.leerWebhook(null, cuerpo))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("Webhook sin firma");
            assertThatThrownBy(() -> p.leerWebhook("   ", cuerpo))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("Webhook sin firma");
            assertThatThrownBy(() -> p.leerWebhook("Bearer algo", null))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("Webhook sin firma");
        }

        @Test
        @DisplayName("Una firma que no verifica con nuestro secreto se rechaza")
        void firmaDeOtroSecreto() {
            var cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
            var tokenAjeno = Jwts.builder().issuer(API_KEY).claim("sha256", sha256Base64(cuerpo))
                    .signWith(Keys.hmacShaKeyFor("otro-secreto-distinto-de-32-bytes-para-hs256".getBytes(StandardCharsets.UTF_8)),
                            Jwts.SIG.HS256)
                    .compact();

            assertThatThrownBy(() -> proveedor().leerWebhook("Bearer " + tokenAjeno, cuerpo))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("Firma del webhook inválida");
        }

        @Test
        @DisplayName("Una firma emitida por otro emisor (issuer distinto) se rechaza")
        void emisorDistinto() {
            var cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
            var tokenOtroEmisor = Jwts.builder().issuer("otro-servidor").claim("sha256", sha256Base64(cuerpo))
                    .signWith(CLAVE, Jwts.SIG.HS256).compact();

            assertThatThrownBy(() -> proveedor().leerWebhook("Bearer " + tokenOtroEmisor, cuerpo))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("Firma del webhook inválida");
        }

        @Test
        @DisplayName("Una firma sin el claim sha256 se rechaza")
        void sinClaimSha256() {
            var cuerpo = "{}".getBytes(StandardCharsets.UTF_8);
            var tokenSinHash = Jwts.builder().issuer(API_KEY).signWith(CLAVE, Jwts.SIG.HS256).compact();

            assertThatThrownBy(() -> proveedor().leerWebhook("Bearer " + tokenSinHash, cuerpo))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("El cuerpo del webhook no coincide con su firma");
        }

        @Test
        @DisplayName("Una firma válida de otro cuerpo no sirve para este cuerpo")
        void cuerpoDistintoAlFirmado() {
            var firmado = "{\"event\":\"room_finished\"}".getBytes(StandardCharsets.UTF_8);
            var recibido = "{\"event\":\"room_started\"}".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> proveedor().leerWebhook("Bearer " + firmaDe(firmado), recibido))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("El cuerpo del webhook no coincide con su firma");
        }

        @Test
        @DisplayName("La cabecera acepta el token con o sin el prefijo Bearer")
        void prefijoBearerOpcional() {
            String evento = "{\"event\":\"room_finished\",\"room\":{\"name\":\"subasta-" + SUBASTA + "\"}}";
            var cuerpo = evento.getBytes(StandardCharsets.UTF_8);
            var p = proveedor();

            assertThat(p.leerWebhook("Bearer " + firmaDe(cuerpo), cuerpo)).isPresent();
            assertThat(p.leerWebhook(firmaDe(cuerpo), cuerpo)).isPresent();
        }

        @Test
        @DisplayName("Un cuerpo que no es JSON se rechaza con su propio mensaje")
        void cuerpoNoJson() {
            var cuerpo = "<html>no soy json</html>".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> proveedor().leerWebhook("Bearer " + firmaDe(cuerpo), cuerpo))
                    .isInstanceOf(LiveKitProveedor.WebhookInvalidoException.class)
                    .hasMessage("El cuerpo del webhook no es JSON");
        }
    }

    @Nested
    class TraduccionDelEvento {

        private Optional<ProveedorDeVideo.EventoDeSala> leer(String evento) {
            var cuerpo = evento.getBytes(StandardCharsets.UTF_8);
            return proveedor().leerWebhook("Bearer " + firmaDe(cuerpo), cuerpo);
        }

        @Test
        @DisplayName("Los cuatro eventos conocidos se traducen a su tipo")
        void eventosConocidos() {
            String campos = "\"room\":{\"name\":\"subasta-" + SUBASTA + "\"},"
                    + "\"participant\":{\"identity\":\"" + USUARIO + "\",\"sid\":\"PA_1\"},"
                    + "\"track\":{\"type\":\"VIDEO\"}";

            assertThat(leer("{\"event\":\"track_published\"," + campos + "}").orElseThrow().tipo())
                    .isEqualTo(LiveKitProveedor.TipoEventoDeSala.PISTA_PUBLICADA);
            assertThat(leer("{\"event\":\"track_unpublished\"," + campos + "}").orElseThrow().tipo())
                    .isEqualTo(LiveKitProveedor.TipoEventoDeSala.PISTA_RETIRADA);
            assertThat(leer("{\"event\":\"participant_left\"," + campos + "}").orElseThrow().tipo())
                    .isEqualTo(LiveKitProveedor.TipoEventoDeSala.PARTICIPANTE_SALIO);
            assertThat(leer("{\"event\":\"room_finished\"," + campos + "}").orElseThrow().tipo())
                    .isEqualTo(LiveKitProveedor.TipoEventoDeSala.SALA_CERRADA);
        }

        @Test
        @DisplayName("Un evento desconocido se marca como OTRO, no se descarta")
        void eventoDesconocido() {
            var evento = leer("{\"event\":\"track_muted\",\"room\":{\"name\":\"subasta-" + SUBASTA + "\"}}")
                    .orElseThrow();

            assertThat(evento.tipo()).isEqualTo(LiveKitProveedor.TipoEventoDeSala.OTRO);
            assertThat(evento.subastaId()).isEqualTo(SUBASTA);
        }

        @Test
        @DisplayName("Una sala que no es de subasta se ignora en silencio")
        void salaAjena() {
            assertThat(leer("{\"event\":\"room_finished\",\"room\":{\"name\":\"otra-sala\"}}")).isEmpty();
            assertThat(leer("{\"event\":\"room_finished\"}")).isEmpty();
        }

        @Test
        @DisplayName("Un sufijo que no es UUID se ignora en silencio")
        void sufijoNoUuid() {
            assertThat(leer("{\"event\":\"room_finished\",\"room\":{\"name\":\"subasta-no-es-uuid\"}}")).isEmpty();
        }

        @Test
        @DisplayName("Solo los tipos de pista de video se marcan como video (VIDEO y 1)")
        void deteccionDePistaDeVideo() {
            String base = "{\"event\":\"track_published\",\"room\":{\"name\":\"subasta-" + SUBASTA + "\"},";

            assertThat(leer(base + "\"track\":{\"type\":\"VIDEO\"}}").orElseThrow().pistaDeVideo()).isTrue();
            assertThat(leer(base + "\"track\":{\"type\":\"1\"}}").orElseThrow().pistaDeVideo()).isTrue();
            assertThat(leer(base + "\"track\":{\"type\":\"AUDIO\"}}").orElseThrow().pistaDeVideo()).isFalse();
            assertThat(leer(base + "\"track\":{}}").orElseThrow().pistaDeVideo()).isFalse();
        }

        @Test
        @DisplayName("Un participante ausente deja identificadores nulos, no vacíos")
        void participanteAusente() {
            var evento = leer("{\"event\":\"participant_joined\",\"room\":{\"name\":\"subasta-" + SUBASTA + "\"}}")
                    .orElseThrow();

            assertThat(evento.participanteId()).isNull();
            assertThat(evento.participanteSid()).isNull();
        }

        @Test
        @DisplayName("Se conservan la identidad y el sid cuando vienen")
        void participantePresente() {
            var evento = leer("{\"event\":\"participant_joined\",\"room\":{\"name\":\"subasta-" + SUBASTA
                    + "\"},\"participant\":{\"identity\":\"usuario-7\",\"sid\":\"PA_9\"}}").orElseThrow();

            assertThat(evento.participanteId()).isEqualTo("usuario-7");
            assertThat(evento.participanteSid()).isEqualTo("PA_9");
        }
    }

    @Nested
    class ApiDeServidor {

        @Test
        @DisplayName("cerrarSala() llama a DeleteRoom con el nombre de la sala")
        void cierraLaSala() {
            codigo.set(200);

            proveedor().cerrarSala(SUBASTA);

            assertThat(rutaRecibida.get()).isEqualTo("/twirp/livekit.RoomService/DeleteRoom");
            assertThat(cuerpoRecibido.get()).contains("subasta-" + SUBASTA);
            assertThat(autorizacionRecibida.get()).startsWith("Bearer ");
        }

        @Test
        @DisplayName("expulsar() llama a RemoveParticipant indicando al participante")
        void expulsaAlParticipante() {
            codigo.set(200);

            proveedor().expulsar(SUBASTA, "usuario-7");

            assertThat(rutaRecibida.get()).isEqualTo("/twirp/livekit.RoomService/RemoveParticipant");
            assertThat(cuerpoRecibido.get()).contains("usuario-7");
        }

        @Test
        @DisplayName("Si la sala ya no existe (404) no es un fallo: es justo lo que se pedía")
        void salaYaNoExiste() {
            codigo.set(404);

            assertThatCode(() -> proveedor().cerrarSala(SUBASTA)).doesNotThrowAnyException();
            assertThatCode(() -> proveedor().expulsar(SUBASTA, "usuario-7")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Si el proveedor no responde, la puja de la base ya está detenida: solo se registra")
        void proveedorCaido() {
            var caido = new LiveKitProveedor("http://localhost:" + puertoCaido, "", API_KEY, API_SECRET, 120,
                    new ObjectMapper());

            assertThatCode(() -> caido.cerrarSala(SUBASTA)).doesNotThrowAnyException();
            assertThatCode(() -> caido.expulsar(SUBASTA, "usuario-7")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Un error del servidor que no es 404 tampoco rompe la operación")
        void errorDelServidor() {
            codigo.set(500);

            assertThatCode(() -> proveedor().cerrarSala(SUBASTA)).doesNotThrowAnyException();
        }
    }
}