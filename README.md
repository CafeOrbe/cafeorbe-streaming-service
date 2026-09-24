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

| `POST /internal/livekit/webhook` | HU-11 | Webhooks de LiveKit, firmados con la clave de su API. No pasa por el api-gateway. |

## Reglas (hallazgos de la verificación de la HU-11)

- **Dueño y estado (11, 17):** antes de iniciar se consulta `GET /api/subastas/{id}` en auction. Solo el `subastadorId` de la subasta puede transmitir (403 si no), no se puede transmitir en una subasta `FINALIZADA` o `DESIERTA` (409) ni en una inexistente (404). Si auction no responde, 503.
- **El Subastador se va sin detener (12):** LiveKit avisa por webhook (`participant_left`, `track_unpublished` del video) y la transmisión se detiene desde el servidor, con su evento `TransmisionDetenida`.
- **Eventos (14):** `TransmisionIniciada` y `TransmisionDetenida` se guardan en la tabla `outbox` en la misma transacción que el cambio de estado y los publica `OutboxPublisher`, igual que en auction. Si RabbitMQ está caído, se reintentan.
- **Token del emisor tras detener (16):** al detener se cierra la sala de LiveKit (`DeleteRoom`). Si alguien vuelve a publicar sin una transmisión activa suya (por ejemplo, con el token viejo), se le expulsa (`RemoveParticipant`).

## Ejecutar

```bash
mvn spring-boot:run      # requiere Postgres (streaming_db), RabbitMQ y LiveKit (docker compose de cafeorbe-infra)
mvn test                 # H2, sin infraestructura
```

Variables: `LIVEKIT_URL` (URL a la que se conecta el **navegador**), `LIVEKIT_API_URL` (API de servidor de LiveKit vista desde este servicio), `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `AUCTION_URL`.
