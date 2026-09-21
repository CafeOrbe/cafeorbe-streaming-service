package com.cafeorbe.streaming;

import com.cafeorbe.contracts.Cabeceras;
import com.cafeorbe.contracts.EventoEnvelope;
import com.cafeorbe.contracts.Eventos;
import com.cafeorbe.streaming.provider.LiveKitProveedor;
import com.cafeorbe.streaming.service.TransmisionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Autowired LiveKitProveedor proveedor;
    @MockitoBean RabbitTemplate rabbit;

    @BeforeEach
    void limpiar() {
        transmisiones.deleteAll();
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

    @Test
    @DisplayName("HU-11 · Inicio de transmisión: entrega credenciales de emisor, queda activa y publica TransmisionIniciada")
    void inicio() throws Exception {
        var res = mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.sala").value("subasta-" + SUBASTA))
                .andExpect(jsonPath("$.url").value("ws://localhost:7880"))
                .andReturn();

        mvc.perform(como(get(url("estado")), ANA, "Ana", "COMPRADOR"))
                .andExpect(jsonPath("$.transmitiendo").value(true));

        var evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(Eventos.EXCHANGE), eq(Eventos.TRANSMISION_INICIADA), evento.capture());
        assertThat(((EventoEnvelope<?>) evento.getValue()).tipo()).isEqualTo(Eventos.TRANSMISION_INICIADA);
        assertThat(res.getResponse().getContentAsString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("HU-11 · Iniciar dos veces no republica el evento")
    void inicioIdempotente() throws Exception {
        mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());
        mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());
        verify(rabbit, times(1)).convertAndSend(eq(Eventos.EXCHANGE), eq(Eventos.TRANSMISION_INICIADA), any(Object.class));
    }

    @Test
    @DisplayName("HU-11 · Detener la transmisión: deja de estar activa y publica TransmisionDetenida")
    void detener() throws Exception {
        mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());

        mvc.perform(como(post(url("detener")), LUIS, "Luis", "SUBASTADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmitiendo").value(false));
        mvc.perform(como(get(url("estado")), ANA, "Ana", "COMPRADOR"))
                .andExpect(jsonPath("$.transmitiendo").value(false));
        verify(rabbit).convertAndSend(eq(Eventos.EXCHANGE), eq(Eventos.TRANSMISION_DETENIDA), any(Object.class));
    }

    @Test
    @DisplayName("Detener sin transmisión activa no falla ni publica nada")
    void detenerSinTransmision() throws Exception {
        mvc.perform(como(post(url("detener")), LUIS, "Luis", "SUBASTADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transmitiendo").value(false));
        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("Solo un Subastador transmite (403 a un Comprador) y otro Subastador no puede detener la transmisión ajena")
    void permisos() throws Exception {
        mvc.perform(como(post(url("iniciar")), ANA, "Ana", "COMPRADOR")).andExpect(status().isForbidden());

        mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());
        mvc.perform(como(post(url("detener")), MARTA, "Marta", "SUBASTADOR")).andExpect(status().isForbidden());
        mvc.perform(post(url("iniciar"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Los compradores obtienen credenciales solo si hay transmisión: 409 La transmisión aún no ha iniciado")
    void credencialesDeEspectador() throws Exception {
        mvc.perform(como(get(url("credenciales")), ANA, "Ana", "COMPRADOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La transmisión aún no ha iniciado"));

        mvc.perform(como(post(url("iniciar")), LUIS, "Luis", "SUBASTADOR")).andExpect(status().isOk());
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
}
