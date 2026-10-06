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
