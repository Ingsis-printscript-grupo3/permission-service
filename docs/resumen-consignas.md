# permission-service: qué se hizo en las consignas 1 a 3

Resumen para el grupo: qué hay, qué se decidió y por qué, y qué les toca a los otros servicios.
El detalle de cada endpoint está en el [README](../README.md).

## Consigna 1: repo y primeros casos de uso

El repo es `permission-service`: Spring Boot 4 + Kotlin, JDK 21, Postgres con Flyway, ktlint y
CI (`./gradlew check`) en cada PR.

El servicio es **genérico**: guarda `resourceType` + `resourceId` y no sabe qué es un snippet. Si
mañana se comparten carpetas, no se toca nada acá.

| Endpoint | Para qué historia |
|---|---|
| `POST /internal/permissions` (OWNER o READ, idempotente: `201` nuevo, `200` si ya estaba) | 1, 3 (crear) y 7 (compartir) |
| `GET /internal/permissions/check` → `OWNER`, `READ` o `NONE` | 2, 4, 6 y todo lo que pide permiso |
| `GET /internal/permissions/resources?relation=OWNED\|SHARED\|ALL` → ids | 5 (listado) |
| `DELETE /internal/permissions` (todos los de un recurso, siempre `204`) | borrar snippet |
| `GET /users?search=` → usuarios de Auth0 | 7 / T3 (elegir con quién compartir) |

Decisiones:

- **Solo ids en `/resources`.** Filtrar, ordenar y paginar lo hace snippet-service con su propia
  base. Si lo hiciera este servicio, tendría que conocer el nombre y el lenguaje de los snippets.
- **Idempotencia en el alta y en el borrado.** snippet-service puede reintentar sin duplicar ni
  fallar.
- **Índice `(user_id, resource_type)`** (migración V2). El unique de V1 empieza por recurso y no
  sirve para "todo lo de un usuario".

## Consigna 2: Docker y seguir con los casos de uso

**Docker.** Dockerfile multi-stage: compila con el JDK y la imagen final lleva solo el JRE y el
jar, con usuario no root. Además:

- `HEALTHCHECK` contra `/actuator/health`. En el compose conviene cambiar
  `permission-service: condition: service_started` por `service_healthy`. Swarm también lo usa
  para reemplazar contenedores colgados.
- `-XX:MaxRAMPercentage=75`. Sin esto la JVM usa solo el 25% de la memoria del contenedor.
- La base es `postgres:17` en el compose (no hace falta Dockerfile propio). Probado: la imagen
  queda `healthy` y Flyway migra contra Postgres real.

**Auth0** (PR #3):

- `GET /users` busca en la Management API. Cada palabra es prefijo de email, nombre o nickname;
  quien busca no aparece; con menos de 2 letras no consulta. Responde con la forma de
  `PaginatedUsers` de la UI de la cátedra.
- Dar `READ` a un usuario que no existe en Auth0 responde **`422`**. Elegí 422 y no 400 para que
  snippet-service lo distinga de un pedido mal armado.
- Auth0 caído o con rate limit responde **`503`**.
- Valida el JWT (firma, `iss`, `aud`, `exp`). El token M2M se cachea hasta que está por vencer.

Trade-offs que conviene conocer:

- **`/internal/**` todavía no pide token.** snippet-service manda `X-User-Id` y exigirlo hoy
  rompería la integración. Mientras tanto lo protege que nginx no lo expone. Cuando
  snippet-service propague el JWT, es una línea en `SecurityConfig`.
- **Auth0 está detrás de una interfaz (`UserDirectory`).** Los tests corren sin red y cambiar de
  proveedor es otra implementación.
- **Todavía no hay tenant.** `AUTH0_DOMAIN` tiene default `auth0-sin-configurar.invalid`. Sin
  credenciales el servicio levanta igual: lo interno anda y solo `/users` y compartir dan `503`.
- **No cacheo las búsquedas.** Con el mínimo de 2 letras alcanza por ahora.

## Consigna 3: branching strategy (propuesta)

La teórica de SCM avisa que "un branch por ambiente" es un antipatrón, porque cada rama termina
con código distinto. Por eso `dev` y `main` no son dos versiones del código: son **el mismo código
en dos momentos**, y la imagen se construye una vez y se promueve (*build once, deploy many*).

- **`dev`**: rama de integración. Todo entra por PR con CI en verde, y es lo que se despliega
  en el ambiente dev.
- **`main`**: lo que está en prod. Solo recibe PRs `dev → main` (un release) o hotfixes.
- **Ramas de trabajo cortas** desde `dev` (`feat/`, `fix/`, `refactor/`, `build/`), mergeadas
  en pocos días para no divergir.
- **Hotfix**: rama desde `main`, PR a `main` y después se mergea `main` en `dev`.
- **Commits** con conventional commits (`feat:`, `fix:`, `build:`…).
- **Cuando haya CD**: push a `dev` publica la imagen `:dev` + `:sha`; el merge a `main` promueve
  ese mismo `:sha` como `:prod`, sin recompilar.

Trade-off: trunk-based (todo a `main`) integra más seguido, pero la consigna pide dev y prod, y
tener `dev` permite probar la integración entre servicios antes de que llegue a prod.

## Qué les toca a los otros

- **snippet-service**:
  - Traducir el `422` de compartir a `400` (historia 7) y el `503` a un error reintentable.
  - Cuando integren Auth0, propagar el JWT a `/internal/**`.
- **compose / infra**:
  - Usar `service_healthy` para permission-service.
  - Pasarle `AUTH0_DOMAIN`, `AUTH0_AUDIENCE`, `AUTH0_M2M_CLIENT_ID` y `AUTH0_M2M_CLIENT_SECRET`
    (los dos últimos por `.env`).
  - nginx: `/api/users/**` va a permission-service y `/internal/**` se bloquea.
- **Auth0** (quien cree el tenant):
  - Una API con el audience que usemos.
  - Una aplicación M2M autorizada en la Management API con el scope `read:users`.

## Pendiente

- **El CI no se dispara solo en los PRs de este repo.** El workflow está activo, pero GitHub no
  crea el check, y en snippet-service sí anda. Por ahora se corre a mano (`workflow_dispatch`). Hay
  que revisarlo antes de exigir checks para mergear.
- Proteger `dev` y `main` (PR obligatorio) cuando el CI ande.
- Validar el JWT también en `/internal/**` (ver arriba).
