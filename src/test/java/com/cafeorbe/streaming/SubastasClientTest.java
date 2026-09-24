package com.cafeorbe.streaming;

import com.cafeorbe.contracts.Rol;
import com.cafeorbe.streaming.api.ErrorDeNegocio;
import com.cafeorbe.streaming.api.UsuarioActual;
import com.cafeorbe.streaming.client.SubastasClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hallazgos 11 y 17: cómo se interpreta la respuesta del auction-service. Usa un servidor HTTP falso local. */
class SubastasClientTest {

    static final UUID SUBASTA = UUID.randomUUID();
    static final UUID LUIS = UUID.randomUUID();
    static final UsuarioActual USUARIO = new UsuarioActual(LUIS, "Luis Pérez", Rol.SUBASTADOR);

    HttpServer servidor;
    final AtomicReference<String> cabeceraNombre = new AtomicReference<>();
    int estado;
    String cuerpo;

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/api/subastas/", intercambio -> {
            cabeceraNombre.set(intercambio.getRequestHeaders().getFirst("X-User-Name"));
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    private SubastasClient cliente() {
        return new SubastasClient("http://localhost:" + servidor.getAddress().getPort(), 1000, 2000);
    }

    @Test
    @DisplayName("Lee el dueño y el estado del detalle de la subasta, ignorando el resto de campos, y reenvía la identidad")
    void leeDuenoYEstado() {
        estado = 200;
        cuerpo = "{\"id\":\"" + SUBASTA + "\",\"nombre\":\"Lote Huila\",\"estado\":\"FINALIZADA\","
                + "\"subastadorId\":\"" + LUIS + "\",\"precioActual\":120,\"ficha\":null,\"ultimasPujas\":[]}";

        var subasta = cliente().consultar(SUBASTA, USUARIO);

        assertThat(subasta.subastadorId()).isEqualTo(LUIS);
        assertThat(subasta.terminada()).isTrue();
        assertThat(cabeceraNombre.get()).isEqualTo("Luis+P%C3%A9rez");
    }

    @Test
    @DisplayName("Subasta inexistente (404 de auction) → 404 La subasta no existe")
    void inexistente() {
        estado = 404;
        cuerpo = "{\"status\":404,\"mensaje\":\"La subasta no existe\",\"campos\":{}}";

        assertThatThrownBy(() -> cliente().consultar(SUBASTA, USUARIO))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessage("La subasta no existe")
                .extracting(e -> ((ErrorDeNegocio) e).getEstado()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Auction caído → 503, nunca se transmite sin verificar")
    void auctionCaido() {
        SubastasClient sinServidor = new SubastasClient("http://localhost:1", 300, 300);

        assertThatThrownBy(() -> sinServidor.consultar(SUBASTA, USUARIO))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).getEstado()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
