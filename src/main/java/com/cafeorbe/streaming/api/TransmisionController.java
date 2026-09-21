package com.cafeorbe.streaming.api;

import com.cafeorbe.streaming.provider.ProveedorDeVideo.Credenciales;
import com.cafeorbe.streaming.service.TransmisionService;
import com.cafeorbe.streaming.service.TransmisionService.Estado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/streaming/subastas/{subastaId}")
public class TransmisionController {

    private final TransmisionService transmisiones;

    public TransmisionController(TransmisionService transmisiones) {
        this.transmisiones = transmisiones;
    }

    /** HU-11: el Subastador inicia la transmisión y recibe credenciales para publicar su cámara. */
    @PostMapping("/iniciar")
    public Credenciales iniciar(UsuarioActual usuario, @PathVariable UUID subastaId) {
        return transmisiones.iniciar(subastaId, usuario);
    }

    @PostMapping("/detener")
    public Estado detener(UsuarioActual usuario, @PathVariable UUID subastaId) {
        return transmisiones.detener(subastaId, usuario);
    }

    /** ¿Hay transmisión activa? Cualquier usuario autenticado. */
    @GetMapping("/estado")
    public Estado estado(UsuarioActual usuario, @PathVariable UUID subastaId) {
        return transmisiones.estado(subastaId);
    }

    /** Credenciales de solo lectura para ver el video. 409 si aún no hay transmisión. */
    @GetMapping("/credenciales")
    public Credenciales credenciales(UsuarioActual usuario, @PathVariable UUID subastaId) {
        return transmisiones.credencialesDeEspectador(subastaId, usuario);
    }
}
