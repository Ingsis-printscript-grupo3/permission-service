# permission-service

Servicio de Snippet Searcher que sabe quién puede qué sobre qué recurso: quién es el owner y con
quién está compartido. Es genérico: guarda `resourceType` + `resourceId` y no sabe qué es un
snippet, así sirve para cualquier recurso que se quiera compartir más adelante.

Los usuarios no se guardan acá: vienen de Auth0.

El diseño de la plataforma está en el repo `snippet-searcher-infra`.

## Correrlo

```bash
./gradlew bootRun
```

Levanta en el puerto `8081`. Para ver que está arriba: `GET http://localhost:8081/actuator/health`.

## Checks

```bash
./gradlew check
```

Corre ktlint y los tests. Es lo mismo que corre el CI en cada PR contra `main` o `dev`.

## Docker

```bash
docker build -t permission-service .
docker run -p 8081:8081 -e DB_URL=jdbc:postgresql://<host>:5432/permissions permission-service
```

La imagen trae un `HEALTHCHECK` contra `/actuator/health`: en el compose se puede esperar al
servicio con `condition: service_healthy`.

## Endpoints

| Método | Path | Quién lo llama | Qué hace |
|---|---|---|---|
| GET | `/users?search=&page=&page_size=` | UI (con el JWT) | Busca usuarios de Auth0 para compartir. Ver abajo |
| POST | `/internal/permissions` | snippet-service | Body `{resourceType, resourceId, userId, type}`, con `type` `OWNER` o `READ`. `201` si el permiso es nuevo, `200` si ya existía (no se duplica) |
| GET | `/internal/permissions/check?resourceType=&resourceId=&userId=` | snippet-service | `{type}`: `OWNER`, `READ` o `NONE` |
| GET | `/internal/permissions/resources?resourceType=&userId=&relation=` | snippet-service | `[{resourceId, type}]` de los recursos del usuario. `relation`: `OWNED` (es owner), `SHARED` (se lo compartieron) o `ALL` (los dos) |
| DELETE | `/internal/permissions?resourceType=&resourceId=` | snippet-service | Borra todos los permisos del recurso. `204` siempre, aunque no tuviera ninguno |

Errores:

- Pedido mal armado (parámetro faltante, `relation` o `type` que no existe, body inválido): `400` con `{error}`.
- Dar `READ` a un usuario que no existe en Auth0: `422` con `{error}`. El snippet-service lo traduce al `400` de la historia 7.
- Auth0 caído, con rate limit o sin credenciales: `503` con `{error}`. Se puede reintentar.

### `GET /users`

Responde con la forma de `PaginatedUsers` de la UI de la cátedra:

```json
{"page": 0, "page_size": 10, "count": 2, "users": [{"id": "auth0|...", "name": "María", "email": "maria@example.com"}]}
```

- Cada palabra de `search` tiene que ser el principio del email, el nombre o el nickname.
- Quien busca no aparece en los resultados (sale del `sub` del token).
- Con menos de 2 letras devuelve una lista vacía sin consultar Auth0.
- `page` arranca en 0 y `page_size` va de 1 a 50 (default 10).

## Seguridad

Es un resource server: valida el JWT de Auth0 (firma, `iss`, `aud` y `exp`).

- `/users` pide token: sin token o con un token inválido, `401`.
- `/internal/**` hoy no pide token, porque solo es alcanzable en la red interna (nginx no lo expone)
  y el snippet-service todavía manda `X-User-Id`. Cuando propague el JWT, pedirlo es cambiar una
  línea en `SecurityConfig`.
- `/actuator/health` es público.

## Variables de entorno

| Variable | Default (para correrlo local) | |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/permissions` | |
| `DB_USER` / `DB_PASSWORD` | `permission` / `permission` | |
| `AUTH0_DOMAIN` | `auth0-sin-configurar.invalid` (todavía no hay tenant) | issuer del JWT y host de la Management API |
| `AUTH0_AUDIENCE` | `https://snippet-searcher-api` | el `aud` que tienen que traer los tokens |
| `AUTH0_M2M_CLIENT_ID` / `AUTH0_M2M_CLIENT_SECRET` | sin default (secreto) | app M2M de Auth0 con `read:users` en la Management API |

Sin las credenciales M2M el servicio levanta igual y los endpoints internos andan. Solo `GET /users`
y dar `READ` responden `503`.
