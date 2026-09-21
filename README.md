# cafeorbe-streaming-service

Señalización del video en vivo. **Arquitectura:** servicio delgado de integración. Puerto `8084`.

El video **no pasa por aquí**: lo mueve LiveKit (WebRTC). Este servicio entrega credenciales, guarda si hay transmisión activa y publica los eventos.

| Método y ruta | Historia | Notas |
|---|---|---|
| `POST /api/streaming/subastas/{id}/iniciar` | HU-11 | Solo Subastador. Marca la transmisión activa, publica `TransmisionIniciada` y devuelve credenciales de emisor. |
| `POST /api/streaming/subastas/{id}/detener` | HU-11 | Publica `TransmisionDetenida`. Otro Subastador no puede detener una transmisión ajena. |
| `GET /api/streaming/subastas/{id}/estado` | HU-11, HU-15 | `{transmitiendo, sala}` para quien entra a la sala. |
| `GET /api/streaming/subastas/{id}/credenciales` | HU-15 | Credenciales de solo lectura. 409 `La transmisión aún no ha iniciado` si no hay video. |

`provider/ProveedorDeVideo` es el único punto de contacto con el proveedor; `LiveKitProveedor` firma los tokens de acceso (JWT HS256) sin necesitar el SDK de LiveKit. Cambiar de proveedor = otra implementación de esa interfaz.

## Limitaciones conocidas (MVP)

- No verifica con auction que el Subastador sea el dueño de esa subasta; solo exige el rol Subastador.
- Si el broker está caído al iniciar/detener, el estado se guarda igual y el evento se pierde (los compradores lo ven al entrar, por `/estado`).

## Ejecutar

```bash
mvn spring-boot:run      # requiere Postgres (streaming_db), RabbitMQ y LiveKit (docker compose de cafeorbe-infra)
mvn test                 # H2, sin infraestructura
```

Variables: `LIVEKIT_URL` (URL a la que se conecta el **navegador**), `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`.
