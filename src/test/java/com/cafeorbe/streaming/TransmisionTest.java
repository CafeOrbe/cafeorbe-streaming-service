package com.cafeorbe.streaming;

import com.cafeorbe.contracts.Cabeceras;
import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.streaming.api.ErrorDeNegocio;
import com.cafeorbe.streaming.client.SubastasClient;
import com.cafeorbe.streaming.client.SubastasClient.SubastaInfo;
import com.cafeorbe.streaming.outbox.OutboxEvento;
import com.cafeorbe.streaming.outbox.OutboxPublisher;
import com.cafeorbe.streaming.outbox.OutboxRepository;
import com.cafeorbe.streaming.provider.LiveKitProveedor;
import com.cafeorbe.streaming.service.TransmisionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransmisionTest {

    static final UUID SUBASTA = UUID.randomUUID();
    static final UUID LUIS = UUID.randomUUID();
    static final UUID MARTA = UUID.randomUUID();
    static final UUID ANA = UUID.randomUUID();
    static final String SECRETO = "cafeorbe-livekit-dev-secret-cambiar-en-prod-0123456789";

    @Autowired MockMvc mvc;
    @Autowired TransmisionRepository transmisiones;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxPublisher publicador;
    @MockitoSpyBean LiveKitProveedor proveedor;
    @MockitoBean SubastasClient subastas;
    @MockitoBean RabbitTemplate rabbit;

    @BeforeEach
    void preparar() {
        transmisiones.deleteAll();
        outbox.deleteAll();
        // Por defecto la subasta es de Luis y está en curso.
        when(subastas.consultar(eq(SUBASTA), any())).thenReturn(new SubastaInfo(SUBASTA, LUIS, "EN_CURSO"));
        doNothing().when(proveedor).cerrarSala(any());
        doNothing().when(proveedor).expulsar(any(), anyString());
    }

    private static MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder req, UUID id, String nombre, String rol) {
        return req.header(Cabeceras.USUARIO_ID, id.toString()).header(Cabeceras.USUARIO_NOMBRE, nombre)
                .header(Cabeceras.USUARIO_ROL, rol);
    }

    private String url(String accion) {
        return "/api/streaming/subastas/" + SUBASTA + "/" + accion;
    }

    private Claims leer(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(token).getPayload();
    }

    private List<String> tiposEnOutbox() {
        return outbox.findAll().stream().map(OutboxEvento::getTipo).toList();
    }

    private void iniciarComoLuis() throws Exception {
        mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());
    }

    private void estadoEsperado(boolean transmitiendo) throws Exception {
        mvc.perform(como(get(url("estado")), ANA, "Ana", "COMPRADOR"))
                .andExpect(jsonPath("$.transmitiendo").value(transmitiendo));
    }

    // ── Webhooks de LiveKit ──────────────────────────────────────────────

    /** Envía un webhook firmado igual que LiveKit: JWT con el hash SHA-256 del cuerpo. */
    private ResultActions webhook(String cuerpo, String secreto) throws Exception {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        String firma = Jwts.builder().issuer("cafeorbe").claim("sha256", hash)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        return mvc.perform(post("/internal/livekit/webhook").contentType("application/webhook+json")
                .header("Authorization", firma).content(bytes));
    }

    private ResultActions webhook(String cuerpo) throws Exception {
        return webhook(cuerpo, SECRETO);
    }

    private static String evento(String tipo, UUID usuario, String sid, String pista) {
        String track = pista == null ? "" : ",\"track\":{\"sid\":\"TR_1\",\"type\":\"" + pista + "\"}";
        return "{\"event\":\"" + tipo + "\",\"id\":\"EV_1\",\"createdAt\":\"1790000000\","
                + "\"room\":{\"name\":\"subasta-" + SUBASTA + "\"},"
                + "\"participant\":{\"sid\":\"" + sid + "\",\"identity\":\"" + usuario + "\"}" + track + "}";
    }

    // ── Escenarios de la HU-11 ───────────────────────────────────────────

    @Test
    @DisplayName("HU-11 · Inicio de transmisión: entrega credenciales de emisor, queda activa y guarda TransmisionIniciada en la outbox")
    void inicio() throws Exception {
        var res = mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.sala").value("subasta-" + SUBASTA))
                .andExpect(jsonPath("$.url").value("ws://localhost:7880"))
                .andReturn();

        estadoEsperado(true);
        assertThat(tiposEnOutbox()).containsExactly(Eventos.TRANSMISION_INICIADA);
        assertThat(res.getResponse().getContentAsString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("HU-11 · Iniciar dos veces no repite el evento")
    void inicioIdempotente() throws Exception {
        iniciarComoLuis();
        iniciarComoLuis();
        assertThat(tiposEnOutbox()).containsExactly(Eventos.TRANSMISION_INICIADA);
    }

    @Test
    @DisplayName("HU-11 · Detener la transmisión: deja de estar activa, guarda TransmisionDetenida y cierra la sala de LiveKit")
    void detener() throws Exception {
        iniciarComoLuis();

        mvc.perform(como(post(url("detener")), LUIS, "Luis", "SUBASTADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmitiendo").value(false));
        estadoEsperado(false);
        assertThat(tiposEnOutbox()).containsExactly(Eventos.TRANSMISION_INICIADA, Eventos.TRANSMISION_DETENIDA);
        verify(proveedor).cerrarSala(SUBASTA);
    }

    @Test
    @DisplayName("Detener sin transmisión activa no falla ni genera eventos")
    void detenerSinTransmision() throws Exception {
        mvc.perform(como(post(url("detener")), LUIS, "Luis", "SUBASTADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmitiendo").value(false));
        assertThat(outbox.count()).isZero();
        verify(proveedor, never()).cerrarSala(any());
    }

    @Test
    @DisplayName("Solo un Subastador transmite (403 a un Comprador) y otro Subastador no puede detener la transmisión ajena")
    void permisos() throws Exception {
        mvc.perform(como(post(url("iniciar")), ANA, "Ana", "COMPRADOR")).andExpect(status().isForbidden());

        iniciarComoLuis();
        mvc.perform(como(post(url("detener")), MARTA, "Marta", "SUBASTADOR")).andExpect(status().isForbidden());
        mvc.perform(post(url("iniciar"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Los compradores obtienen credenciales solo si hay transmisión: 409 La transmisión aún no ha iniciado")
    void credencialesDeEspectador() throws Exception {
        mvc.perform(como(get(url("credenciales")), ANA, "Ana", "COMPRADOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La transmisión aún no ha iniciado"));

        iniciarComoLuis();
        mvc.perform(como(get(url("credenciales")), ANA, "Ana", "COMPRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Token de LiveKit: el emisor puede publicar y el espectador solo suscribirse a la sala de la subasta")
    @SuppressWarnings("unchecked")
    void permisosDelToken() {
        var emisor = leer(proveedor.credencialesDeEmisor(SUBASTA, LUIS, "Luis").token());
        var espectador = leer(proveedor.credencialesDeEspectador(SUBASTA, ANA, "Ana").token());

        assertThat(emisor.getIssuer()).isEqualTo("cafeorbe");
        assertThat(emisor.getSubject()).isEqualTo(LUIS.toString());
        Map<String, Object> videoEmisor = emisor.get("video", Map.class);
        Map<String, Object> videoEspectador = espectador.get("video", Map.class);
        assertThat(videoEmisor).containsEntry("room", "subasta-" + SUBASTA).containsEntry("canPublish", true);
        assertThat(videoEspectador).containsEntry("room", "subasta-" + SUBASTA)
                .containsEntry("canPublish", false).containsEntry("canSubscribe", true);
    }

    // ── Hallazgos 11 y 17: dueño y estado de la subasta ─────────────────

    @Test
    @DisplayName("Hallazgo 11 · Otro Subastador no puede iniciar la transmisión en una subasta ajena, y el dueño sí puede después")
    void subastaAjena() throws Exception {
        mvc.perform(como(post(url("iniciar")), MARTA, "Marta", "SUBASTADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Solo el Subastador de esta subasta puede transmitir"));
        estadoEsperado(false);
        assertThat(outbox.count()).isZero();

        iniciarComoLuis();
        mvc.perform(como(post(url("detener")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Hallazgo 17 · No se puede transmitir en una subasta finalizada o desierta")
    void subastaTerminada() throws Exception {
        for (String estado : List.of("FINALIZADA", "DESIERTA")) {
            when(subastas.consultar(eq(SUBASTA), any())).thenReturn(new SubastaInfo(SUBASTA, LUIS, estado));
            mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensaje").value("La subasta ya terminó; no se puede transmitir"));
        }
        estadoEsperado(false);
        assertThat(transmisiones.count()).isZero();
    }

    @Test
    @DisplayName("Hallazgo 17 · Subasta inexistente: 404 y no se crea ningún registro ni se entregan credenciales")
    void subastaInexistente() throws Exception {
        when(subastas.consultar(eq(SUBASTA), any())).thenThrow(ErrorDeNegocio.noEncontrado("La subasta no existe"));
        mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La subasta no existe"))
                .andExpect(jsonPath("$.token").doesNotExist());
        assertThat(transmisiones.count()).isZero();
    }

    // ── Hallazgo 14: outbox ──────────────────────────────────────────────

    @Test
    @DisplayName("Hallazgo 14 · El publicador envía los eventos pendientes y, si RabbitMQ falla, los conserva para reintentar")
    void outboxReintenta() throws Exception {
        iniciarComoLuis();

        doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("caído")))
                .when(rabbit).send(anyString(), anyString(), any(Message.class));
        assertThat(publicador.publicarPendientes()).isZero();
        assertThat(outbox.countByPublicadoEnIsNull()).isEqualTo(1);

        doNothing().when(rabbit).send(anyString(), anyString(), any(Message.class));
        assertThat(publicador.publicarPendientes()).isEqualTo(1);
        assertThat(outbox.countByPublicadoEnIsNull()).isZero();
        verify(rabbit, org.mockito.Mockito.atLeastOnce())
                .send(eq(Eventos.EXCHANGE), eq(Eventos.TRANSMISION_INICIADA), any(Message.class));
    }

    // ── Hallazgo 12: el Subastador se va sin pulsar "Detener" ─────────────

    @Test
    @DisplayName("Hallazgo 12 · Si el emisor sale de la sala de LiveKit (cierra la pestaña), la transmisión se detiene y se avisa")
    void emisorSaleDeLaSala() throws Exception {
        iniciarComoLuis();
        webhook(evento("track_published", LUIS, "PA_1", "VIDEO")).andExpect(status().isOk());
        estadoEsperado(true);

        webhook(evento("participant_left", LUIS, "PA_1", null)).andExpect(status().isOk());

        estadoEsperado(false);
        assertThat(tiposEnOutbox()).containsExactly(Eventos.TRANSMISION_INICIADA, Eventos.TRANSMISION_DETENIDA);
        verify(proveedor).cerrarSala(SUBASTA);
    }

    @Test
    @DisplayName("Hallazgo 12 · Si el emisor deja de publicar su video, la transmisión se detiene")
    void emisorRetiraElVideo() throws Exception {
        iniciarComoLuis();
        webhook(evento("track_published", LUIS, "PA_1", "VIDEO")).andExpect(status().isOk());
        webhook(evento("track_unpublished", LUIS, "PA_1", "VIDEO")).andExpect(status().isOk());
        estadoEsperado(false);
    }

    @Test
    @DisplayName("Hallazgo 12 · La salida de una sesión vieja del Subastador no corta la transmisión nueva")
    void sesionViejaNoDetiene() throws Exception {
        iniciarComoLuis();
        webhook(evento("track_published", LUIS, "PA_nueva", "VIDEO")).andExpect(status().isOk());

        webhook(evento("participant_left", LUIS, "PA_vieja", null)).andExpect(status().isOk());

        estadoEsperado(true);
        assertThat(tiposEnOutbox()).containsExactly(Eventos.TRANSMISION_INICIADA);
    }

    @Test
    @DisplayName("Webhook sin firma o con firma de otra clave: 401 y no cambia nada")
    void webhookSinFirma() throws Exception {
        iniciarComoLuis();
        webhook(evento("track_published", LUIS, "PA_1", "VIDEO")).andExpect(status().isOk());

        webhook(evento("participant_left", LUIS, "PA_1", null), "otra-clave-que-no-es-la-de-livekit-0123456789")
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/livekit/webhook").contentType("application/webhook+json")
                        .content(evento("participant_left", LUIS, "PA_1", null)))
                .andExpect(status().isUnauthorized());

        estadoEsperado(true);
    }

    // ── Hallazgo 16: token del emisor después de detener ─────────────────

    @Test
    @DisplayName("Hallazgo 16 · Si alguien vuelve a publicar con un token viejo tras detener, se le expulsa de la sala")
    void tokenViejoTrasDetener() throws Exception {
        iniciarComoLuis();
        mvc.perform(como(post(url("detener")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());
        verify(proveedor).cerrarSala(SUBASTA);

        webhook(evento("track_published", LUIS, "PA_2", "VIDEO")).andExpect(status().isOk());

        verify(proveedor).expulsar(SUBASTA, LUIS.toString());
        estadoEsperado(false);
    }
}
