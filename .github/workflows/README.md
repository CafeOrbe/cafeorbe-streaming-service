# Pipeline `ci.yml`

`ci` compila `cafeorbe-contracts`, ejecuta `mvn -B verify` y espera el Quality Gate de Sonar. `build-push` publica la imagen en Azure Container Registry y `deploy-qa` actualiza la aplicación en Azure Container Apps y corre el smoke test. `deploy-prod` solo se ejecuta con tags `v*`.

## Secretos

| Nombre | Uso |
| --- | --- |
| `AZURE_CREDENTIALS` | inicio de sesión en Azure |
| `ACR_USERNAME`, `ACR_PASSWORD` | push de la imagen a `vars.ACR_NAME`.azurecr.io |
| `SONAR_TOKEN` | Quality Gate (secreto de organización) |
| `JWT_SECRET` | firma y validación de la sesión |
| `INTERNAL_TOKEN` | credencial interna SEG-02; **el mismo valor en los 6 servicios** |
| `DB_PASSWORD` | PostgreSQL |
| `RABBIT_PASSWORD` | CloudAMQP |
| `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` | LiveKit Cloud |
| `OTLP_METRICS_AUTH` | autenticación de métricas OTLP (opcional) |

## Variables

| Nombre | Por defecto | Uso |
| --- | --- | --- |
| `ACR_NAME` | — | registro de imágenes |
| `OTLP_METRICS_ENABLED` | `false` | exportar métricas |
| `OTLP_METRICS_URL` | `http://localhost:4318/v1/metrics` | endpoint OTLP |

`deploy-qa` aborta antes de tocar Azure si falta `INTERNAL_TOKEN`. El smoke test comprueba `/actuator/prometheus` con `X-Internal-Token`, así que también valida que el token coincide con el del gateway.
