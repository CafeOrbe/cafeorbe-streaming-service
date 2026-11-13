package com.cafeorbe.streaming;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * SEG-02: en producción todas las peticiones llegan del api-gateway o de otro servicio con la credencial
 * interna. Las pruebas de la API usan estos constructores para hacer lo mismo.
 */
public final class PeticionesInternas {

    public static final String CREDENCIAL = "credencial-de-prueba";

    private PeticionesInternas() {
    }

    public static MockHttpServletRequestBuilder get(String url, Object... variables) {
        return MockMvcRequestBuilders.get(url, variables).header("X-Internal-Token", CREDENCIAL);
    }

    public static MockHttpServletRequestBuilder post(String url, Object... variables) {
        return MockMvcRequestBuilders.post(url, variables).header("X-Internal-Token", CREDENCIAL);
    }
}
