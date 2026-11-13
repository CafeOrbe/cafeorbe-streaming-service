package com.cafeorbe.streaming;

import com.cafeorbe.contracts.Cabeceras;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEG-02: streaming solo atiende peticiones con la credencial interna, salvo el webhook firmado de LiveKit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadInternaTest {

    @Autowired MockMvc mvc;
    @MockitoBean RabbitTemplate rabbit;

    @Test
    @DisplayName("SEG-02 · Un Subastador fabricado sin credencial no puede iniciar una transmisión: 403")
    void sinCredencial() throws Exception {
        mvc.perform(post("/api/streaming/subastas/" + UUID.randomUUID() + "/iniciar")
                        .header(Cabeceras.USUARIO_ID, UUID.randomUUID().toString())
                        .header(Cabeceras.USUARIO_NOMBRE, "x").header(Cabeceras.USUARIO_ROL, "SUBASTADOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El webhook de LiveKit no lleva credencial interna: lo valida la firma de LiveKit (401 sin firma)")
    void webhookSinCredencial() throws Exception {
        mvc.perform(post("/internal/livekit/webhook").contentType("application/webhook+json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
