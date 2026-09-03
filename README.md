# Back-end Drivique

API REST unica para los clientes web y movil de Drivique. La logica de negocio,
seguridad y acceso a PostgreSQL pertenecen al backend.

## Tecnologia

- Java 21
- Spring Boot 4.0.8
- Maven Wrapper
- PostgreSQL, Spring Data JPA y Flyway
- Spring Security, validacion, OpenAPI/Swagger y Actuator
- Docker Compose para PostgreSQL local

## Perfiles

| Perfil | Uso | Configuracion de base de datos |
| --- | --- | --- |
| `dev` | Desarrollo local | Valores locales configurables con variables de entorno |
| `qa` | Pruebas de calidad | Todas las credenciales son obligatorias como variables de entorno |
| `main` | Produccion | Todas las credenciales son obligatorias como variables de entorno |

Nunca se versionan secretos. Copie `.env.example` para documentar sus valores
locales, pero exporte las variables antes de ejecutar la aplicacion.

## Ejecucion local

```powershell
docker compose up -d postgres
$env:DB_PASSWORD = "drivique"
.\mvnw.cmd spring-boot:run
```

La salud de la aplicacion queda disponible en `GET /api/actuator/health`.
La documentacion OpenAPI queda disponible en `/api/swagger-ui.html`.

## Migraciones

Todo cambio de esquema se agrega como una migracion versionada en
`src/main/resources/db/migration`. Hibernate valida el modelo; no crea ni
modifica tablas automaticamente.

## Flujo de ramas

Las ramas padre son `dev`, `qa` y `main`. El trabajo se realiza en ramas hijas
creadas desde su padre y con el sufijo del ambiente:

```text
dev  -> feature/HU-XX-descripcion-dev  -> PR a dev
qa   -> fix/HU-XX-descripcion-qa       -> PR a qa
main -> hotfix/HU-XX-descripcion-main  -> PR a main
```

No se crean PR entre ramas padre. La primera estructura se publica en
`chore/bootstrap-structure-dev`, hija de `dev`, para que su PR apunte a `dev`.
