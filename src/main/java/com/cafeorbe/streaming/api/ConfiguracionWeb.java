package com.cafeorbe.streaming.api;

import com.cafeorbe.contracts.Cabeceras;
import com.cafeorbe.contracts.Rol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    /** Construye {@link UsuarioActual} desde las cabeceras X-User-*. El nombre viaja codificado como URL (UTF-8). */
    static class UsuarioActualResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parametro) {
            return UsuarioActual.class.equals(parametro.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer contenedor,
                                      NativeWebRequest peticion, WebDataBinderFactory fabrica) {
            try {
                UUID id = UUID.fromString(peticion.getHeader(Cabeceras.USUARIO_ID));
                String nombre = URLDecoder.decode(peticion.getHeader(Cabeceras.USUARIO_NOMBRE), StandardCharsets.UTF_8);
                return new UsuarioActual(id, nombre, Rol.valueOf(peticion.getHeader(Cabeceras.USUARIO_ROL)));
            } catch (RuntimeException e) {
                throw ErrorDeNegocio.sinSesion();
            }
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UsuarioActualResolver());
    }

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    org.springframework.amqp.core.TopicExchange eventosExchange() {
        return new org.springframework.amqp.core.TopicExchange(com.cafeorbe.contracts.Eventos.EXCHANGE, true, false);
    }

    @Bean
    org.springframework.amqp.support.converter.MessageConverter messageConverter(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter(objectMapper);
    }
}
