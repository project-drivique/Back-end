# Drivique Backend

API REST compartida por web y móvil: Java 21, Spring Boot 4.0.8, PostgreSQL 17,
Spring Data JPA, Spring Security, Springdoc OpenAPI y Actuator.

## Despliegue en producción verificable (Hostinger)

El backend productivo se encuentra desplegado y activo en **Hostinger** bajo el dominio oficial:

- **Base URL API:** `https://drivique.tech/api/v1`
- **Healthcheck público:** `GET https://drivique.tech/api/actuator/health` -> `200 OK` (`{"status":"UP"}`)
- **Catálogo público:** `GET https://drivique.tech/api/v1/vehicles/search`
- **Cliente Web:** `https://drivique.tech`
- **Cliente Móvil (Web/PWA):** `https://app.drivique.tech`

## HU-BE-01: configuración y conexión

Los perfiles `dev`, `qa` y `main` comparten la configuración segura del datasource y exigen secretos explícitos (`app.security.require-secrets=true`).
Se requieren obligatoriamente:

- `DB_USERNAME` y `DB_PASSWORD` (sin contraseñas predeterminadas en ningún perfil).
- `JWT_SECRET` (mínimo 32 caracteres).
- `APP_DOCUMENT_ENCRYPTION_KEY` (mínimo 32 caracteres para cifrado AES-GCM de documentos KYC).
- Una de estas alternativas de conexión a PostgreSQL 17:
  - `DB_URL`: URL JDBC completa, sin credenciales embebidas.
  - `DB_HOST`, `DB_PORT` y `DB_NAME`: componentes de la conexión.

`.env.example` documenta únicamente marcadores de posición (`placeholders`) sin secretos ni correos reales.

Desde PowerShell, para levantar el entorno local desde cero:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5432'
$env:DB_NAME = 'drivique_dev'
$env:DB_USERNAME = 'drivique'
$secret = Read-Host 'Contraseña de PostgreSQL local' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
$env:JWT_SECRET = 'clave-local-desarrollo-minimo-32-caracteres-seguros'
$env:APP_DOCUMENT_ENCRYPTION_KEY = 'llave-local-cifrado-kyc-minimo-32-caracteres'
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

## Modelo de dominio y esquema de base de datos

El repositorio [database](https://github.com/project-drivique/database) gestiona los changelogs y migraciones Liquibase para los **10 esquemas** (`core`, `iam`, `location`, `fleet`, `catalog`, `rental`, `contract`, `billing`, `support`, `audit`) y las **78 tablas** del modelo relacional.

Este backend implementa **73 entidades JPA** y **53 controladores REST** alineados con el esquema de `database`. En ejecución (`dev`, `qa`, `main`), Hibernate opera con `ddl-auto: validate` para garantizar que las 73 entidades JPA coincidan con las tablas migradas por Liquibase sin modificar el esquema en caliente.

En las pruebas de integración (`DatabaseHealthTestSupport`), Testcontainers levanta `postgres:17-alpine`, aplica las migraciones DDL de Liquibase (`databasechangelog`) y ejecuta la suite de verificación sobre el esquema migrado.

## Contenedor Docker endurecido

El `Dockerfile` construye una imagen multi-etapa con **Eclipse Temurin 21** (`eclipse-temurin:21-jdk` -> `eclipse-temurin:21-jre`):

- Se ejecuta con usuario sin privilegios (`USER drivique`, grupo `drivique`).
- Incluye `HEALTHCHECK` nativo contra `http://localhost:8080/api/actuator/health`.

Construcción y ejecución reproducible del contenedor:

```powershell
docker build -t drivique-api:latest .
docker run --rm -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=main `
  -e DB_HOST=host.docker.internal `
  -e DB_PORT=5432 `
  -e DB_NAME=drivique_db `
  -e DB_USERNAME=drivique `
  -e DB_PASSWORD="<password>" `
  -e JWT_SECRET="<jwt-secret-min-32-chars>" `
  -e APP_DOCUMENT_ENCRYPTION_KEY="<kyc-encryption-key-min-32-chars>" `
  drivique-api:latest
```

## Pool HikariCP

| Variable | Valor predeterminado |
| --- | --- |
| `DB_POOL_MAX_SIZE` | 10 |
| `DB_POOL_MIN_IDLE` | 2 |
| `DB_CONNECTION_TIMEOUT_MS` | 30000 |
| `DB_VALIDATION_TIMEOUT_MS` | 5000 |
| `DB_IDLE_TIMEOUT_MS` | 600000 |
| `DB_MAX_LIFETIME_MS` | 1800000 |

## Contrato OpenAPI y Verificación

- Especificación OpenAPI 3.0 exportada y versionada en [`docs/openapi.json`](docs/openapi.json) (y sincronizada en `Documentation/docs/07-backend/contracts/openapi/openapi.json`).
- `GET /api/actuator/health`: estado general y componentes, incluido `db`, sin exponer detalles sensibles.
- `GET /api/actuator/info`: metadatos públicos de versión de la API.
- Swagger UI (en perfiles de desarrollo): `/api/swagger-ui.html`.

```powershell
Invoke-RestMethod http://localhost:8080/api/actuator/health
Invoke-RestMethod http://localhost:8080/api/actuator/info
.\mvnw.cmd clean verify
```

## Gobernanza de ramas

`HU-*-dev` nace de `dev`; su PR apunta a `dev`. Para promover, se crea `HU-*-qa` desde `qa`, se integra la rama `-dev` y se abre PR hacia `qa`. Posteriormente se crea `HU-*-main` desde `main`, se integra `-qa` y se abre PR hacia `main` con revisión obligatoria y CI en verde.

## Referencias de historias de usuario y arquitectura

Consultar el repositorio **Documentation** (`docs/07-backend/user-stories/`) y [la arquitectura del código](docs/architecture.md) para el detalle por módulo (`iam`, `location`, `fleet`, `catalog`, `rental`, `contract`, `billing`, `support`, `audit`).
