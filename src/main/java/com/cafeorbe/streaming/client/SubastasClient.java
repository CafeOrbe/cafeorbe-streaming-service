package com.cafeorbe.streaming.client;

import com.cafeorbe.contracts.Cabeceras;
import com.cafeorbe.streaming.api.ErrorDeNegocio;
import com.cafeorbe.streaming.api.UsuarioActual;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consulta al auction-service quién es el dueño de una subasta y en qué estado está (hallazgos 11 y 17).
 * El streaming-service no guarda subastas: auction es la única fuente de verdad.
 */
@Component
public class SubastasClient {

    /** Lo único que interesa del detalle de la subasta para decidir si se puede transmitir. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubastaInfo(UUID id, UUID subastadorId, String estado) {

        public boolean terminada() {
            return "FINALIZADA".equals(estado) || "DESIERTA".equals(estado);
        }
    }

    private final RestClient cliente;

    public SubastasClient(@Value("${cafeorbe.auction.url}") String url,
                          @Value("${cafeorbe.auction.connect-timeout-ms:1000}") int conexionMs,
                          @Value("${cafeorbe.auction.read-timeout-ms:2000}") int lecturaMs) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexionMs);
        fabrica.setReadTimeout(lecturaMs);
        this.cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    /**
     * @throws ErrorDeNegocio 404 si la subasta no existe; 503 si auction no responde
     */
    public SubastaInfo consultar(UUID subastaId, UsuarioActual usuario) {
        try {
            SubastaInfo subasta = cliente.get().uri("/api/subastas/{id}", subastaId)
                    .header(Cabeceras.USUARIO_ID, usuario.id().toString())
                    .header(Cabeceras.USUARIO_NOMBRE, URLEncoder.encode(usuario.nombre(), StandardCharsets.UTF_8))
                    .header(Cabeceras.USUARIO_ROL, usuario.rol().name())
                    .retrieve()
                    .body(SubastaInfo.class);
            if (subasta == null) {
                throw ErrorDeNegocio.noDisponible("No se pudo verificar la subasta, intenta de nuevo");
            }
            return subasta;
        } catch (HttpClientErrorException.NotFound e) {
            throw ErrorDeNegocio.noEncontrado("La subasta no existe");
        } catch (RestClientException e) {
            throw ErrorDeNegocio.noDisponible("No se pudo verificar la subasta, intenta de nuevo");
        }
    }
}
