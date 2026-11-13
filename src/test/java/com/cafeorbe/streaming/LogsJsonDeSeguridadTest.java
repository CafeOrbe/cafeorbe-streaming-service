package com.cafeorbe.streaming;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OBS-01 / SEG-02: los eventos de seguridad quedan en el log JSON de Azure con un solo correlationId (ver LogsJson).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogsJsonDeSeguridadTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("OBS-01 · El acceso sin credencial interna queda en el log JSON con un solo correlationId")
    void accesoInternoRechazadoQuedaEnElLog() throws Exception {
        try (var logs = LogsJson.de("cafeorbe.seguridad")) {
            mvc.perform(get("/api/streaming/subastas/00000000-0000-0000-0000-000000000001/estado").header("X-Correlation-Id", "corr-logs-json-1"))
                    .andExpect(status().isForbidden());

            logs.registradoUnaVez("acceso_interno_rechazado", "corr-logs-json-1");
        }
    }
}
