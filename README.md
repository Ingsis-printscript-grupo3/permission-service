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
docker run -p 8081:8081 permission-service
```

## Endpoints

Todos son internos: solo los llama el `snippet-service`, nunca la UI.

| Método | Path | Qué hace |
|---|---|---|
| POST | `/internal/permissions` | Body `{resourceType, resourceId, userId, type}`, con `type` `OWNER` o `READ`. `201` si el permiso es nuevo, `200` si ya existía (no se duplica) |
| GET | `/internal/permissions/check?resourceType=&resourceId=&userId=` | `{type}`: `OWNER`, `READ` o `NONE` |
| GET | `/internal/permissions/resources?resourceType=&userId=&relation=` | `[{resourceId, type}]` de los recursos del usuario. `relation`: `OWNED` (es owner), `SHARED` (se lo compartieron) o `ALL` (los dos) |
| DELETE | `/internal/permissions?resourceType=&resourceId=` | Borra todos los permisos del recurso. `204` siempre, aunque no tuviera ninguno |

Los errores del pedido (parámetro faltante, `relation` o `type` que no existe, body inválido) son
`400` con `{error}`.

## Variables de entorno

| Variable | Default (para correrlo local) |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/permissions` |
| `DB_USER` / `DB_PASSWORD` | `permission` / `permission` |
