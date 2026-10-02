# Drivique Backend

API REST compartida por web y móvil: Java 21, Spring Boot 4.0.8, PostgreSQL 17,
Spring Data JPA, Spring Security y Actuator.

## HU-BE-01: configuración y conexión

Los perfiles `dev`, `qa` y `main` comparten la configuración segura del datasource.
`dev` es el predeterminado; `main` corresponde a producción. Se requieren
`DB_USERNAME`, `DB_PASSWORD` y una de estas alternativas:

- `DB_URL`: URL JDBC completa, sin credenciales embebidas.
- `DB_HOST`, `DB_PORT` y `DB_NAME`: componentes de la conexión.

No hay contraseñas predeterminadas. `.env.example` documenta las variables;
Spring Boot no carga un archivo `.env` automáticamente. Docker Compose sí lo
utiliza, pero las variables deben exportarse también para ejecutar Maven.

Desde PowerShell, para una base local nueva:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5432'
$env:DB_NAME = 'drivique_dev'
$env:DB_USERNAME = 'drivique'
$secret = Read-Host 'Contraseña de PostgreSQL local' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

Si existe `DB_URL` en el entorno, esa variable tiene prioridad sobre host/puerto/nombre.
En QA/producción, el entorno de despliegue suministra las mismas variables y
`SPRING_PROFILES_ACTIVE`. No ejecutar Compose sobre datos existentes esperando
que cambie automáticamente la contraseña del volumen.

## Responsabilidad de la base de datos

El repositorio [database](https://github.com/project-drivique/database) es el único
responsable del SQL, los changelogs, las migraciones Liquibase y los datos semilla.
Este backend no incluye, empaqueta, descarga ni ejecuta esos archivos.

Antes de integrar funcionalidades con persistencia, aplicar desde `database` las
migraciones correspondientes al ambiente y configurar aquí la conexión. Hibernate
usa `ddl-auto: validate`: valida las entidades que estén implementadas y no crea
ni actualiza tablas. Actualmente no hay entidades de negocio, por lo que el estado
`UP` de `db` acredita conectividad, no la existencia de todos los módulos del esquema.

El criterio original de HU-BE-01 «Liquibase valida al iniciar la aplicación» se
reemplaza por la separación de responsabilidades acordada. La
validación de migraciones corresponde al flujo de `database`, previo al backend.

## Pool HikariCP

| Variable | Valor predeterminado |
| --- | --- |
| `DB_POOL_MAX_SIZE` | 10 |
| `DB_POOL_MIN_IDLE` | 2 |
| `DB_CONNECTION_TIMEOUT_MS` | 30000 |
| `DB_VALIDATION_TIMEOUT_MS` | 5000 |
| `DB_IDLE_TIMEOUT_MS` | 600000 |
| `DB_MAX_LIFETIME_MS` | 1800000 |

## Verificación

- `GET /api/actuator/health`: estado general y componentes, incluido `db`, sin
  detalles de conexión, consultas ni credenciales.
- `GET /api/actuator/info`: nombre y versión de la API obtenida de Maven.
- Swagger UI: `/api/swagger-ui.html`.

```powershell
Invoke-RestMethod http://localhost:8080/api/actuator/health
Invoke-RestMethod http://localhost:8080/api/actuator/info
.\mvnw.cmd clean verify
```

Las pruebas necesitan Docker activo. Testcontainers crea un PostgreSQL 17 temporal,
prueba los tres perfiles y los endpoints reales,
la configuración HikariCP y la ausencia de motores de migración. Desde HU-BE-03,
solo las pruebas generan sus tablas en el contenedor mediante Hibernate create-drop;
los perfiles de ejecución conservan validate y no crean tablas.
No utiliza la base local del equipo ni H2. GitHub Actions ejecuta el mismo comando
en PR hacia `dev`, `qa` y `main`.

## Ramas

`HU-BE-01-dev` nace de `dev`; su PR apunta a `dev`. Para promover, crear
`HU-BE-01-qa` desde `qa`, integrar la hija `-dev` y abrir PR hacia `qa`.
Luego crear `HU-BE-01-main` desde `main`, integrar la hija `-qa` y abrir PR hacia
`main`. No se fusionan directamente las ramas padre.

## Contrato de API y errores

Consultar [HU-BE-02](docs/HU-BE-02.md) para Swagger, OpenAPI y el contrato Problem Details.

## Catálogos del sistema

Consultar [HU-BE-03](docs/HU-BE-03.md) para idiomas, monedas, tasas, caché y pendientes de integración.

Consultar [HU-BE-04](docs/HU-BE-04.md) para configuración de marca, tema y parámetros públicos.

Consultar [HU-BE-11](docs/HU-BE-11.md) para consentimientos legales, trazabilidad de Habeas Data y la configuración de las versiones vigentes.

Consultar [HU-BE-12](docs/HU-BE-12.md) para departamentos, ciudades y sus puntos de aeropuerto o terminal.

Consultar [HU-BE-13](docs/HU-BE-13.md) para sedes físicas, horarios y pagos en efectivo.

Consultar [HU-BE-15](docs/HU-BE-15.md) para los catálogos técnicos de flota:
marcas, transmisiones, combustibles y estados habilitados para reserva.

Consultar [HU-BE-21](docs/HU-BE-21.md) para la programación, finalización e historial
de mantenimientos de vehículos.

Consultar [HU-BE-22](docs/HU-BE-22.md) para servicios adicionales y coberturas de seguro.

Consultar [HU-BE-23](docs/HU-BE-23.md) para planes de kilometraje.

Consultar [HU-BE-24](docs/HU-BE-24.md) para validación de promociones y cupones.

Consultar [HU-BE-25](docs/HU-BE-25.md) para el cotizador de alquiler.

Consultar [HU-BE-31](docs/HU-BE-31.md) para firma biométrica y PDF contractual.

Consultar [HU-BE-16](docs/HU-BE-16.md) para categorías de vehículos, tarifas base
y depósitos de garantía.

Consultar [HU-BE-17](docs/HU-BE-17.md) para catálogo y búsqueda pública de flota
con filtros dinámicos y destacados.

Consultar [la arquitectura del código](docs/architecture.md) para la organización por módulos y responsabilidades.
