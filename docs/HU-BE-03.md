# HU-BE-03 — Idiomas, monedas y tasas de cambio

## Contrato y responsabilidades

Entidades: `core.languages`, `core.currencies`, `core.exchange_rates`. Contrato
contrastado con `project-drivique/database`, commit
`d1e70cb592cb7c3be2eb1c3e683562353ad9043a` (HU-BD-02/03).
No se incluyen SQL, changelogs ni motores de migración en el backend.
Aplicar las migraciones desde `database` antes de arrancar: Hibernate mantiene
`ddl-auto: validate`. Las nuevas entidades hacen necesaria la existencia de estas tablas.

## Endpoints

| Método y ruta bajo `/api` | Acceso | Resultado |
| --- | --- | --- |
| `GET /v1/languages` | Público | Idiomas activos ordenados por código |
| `GET /v1/currencies` | Público | Monedas activas ordenadas por código |
| `GET /v1/exchange-rates/latest` | Público | Última tasa persistida por cada par de monedas activas |
| `POST /v1/languages` | SUPER_ADMIN | Crea idioma activo, no predeterminado; 201 |
| `PATCH /v1/languages/{id}/toggle-status` | SUPER_ADMIN | Alterna estado activo; 200 o 404 |
| `POST /v1/exchange-rates/sync` | SUPER_ADMIN | Importa cotizaciones del proveedor; 503 si no está disponible/configurado |

Solicitud para crear idioma: `{"code":"es","name":"Español"}`. El código cumple
`^[a-z]{2}(-[A-Z]{2})?$`; el nombre es obligatorio y tiene máximo 50 caracteres.
Duplicados de código o nombre devuelven 409; errores de validación devuelven 400.
La escritura exige `ROLE_SUPER_ADMIN` en el contexto de Spring Security, validada
mediante `@PreAuthorize`. El login/JWT se implementa en HU-BE-06; no se agregan
credenciales de prueba ni accesos administrativos abiertos a la aplicación.

DTO de salida: `LanguageResponseDTO`, `CurrencyResponseDTO` y
`ExchangeRateResponseDTO`. No se serializan entidades JPA. Swagger documenta rutas,
esquemas y errores. Las respuestas fallidas siguen el contrato de HU-BE-02.

## Caché

`@Cacheable` sobre idiomas activos y últimas tasas. Caffeine guarda resultados en
memoria durante cinco minutos, con máximo 256 entradas por caché. Crear o alternar
un idioma invalida la caché de idiomas después del commit; sincronizar tasas hace
lo mismo con su caché. Los cambios externos a esta instancia se reflejan al vencer
el TTL. No es una caché distribuida.

La consulta de tasas no calcula inversas, no convierte importes y no inventa una
cotización cuando no hay datos. Devuelve la fecha y el proveedor de cada registro;
"latest" significa última tasa almacenada, no precio garantizado en tiempo real.

## Proveedor: Frankfurter v2

Integración gratuita sin registro ni API key: https://frankfurter.dev/.
El adaptador consulta `/v2/rates` por cada moneda activa, filtrando las otras
monedas activas como destino. Para COP, USD y EUR son tres consultas por sincronización.
No hay tareas automáticas: la sincronización se solicita mediante el endpoint administrativo.

Configuración opcional: `FRANKFURTER_BASE_URL` (por defecto
`https://api.frankfurter.dev`) y `FRANKFURTER_TIMEOUT_MS` (5000 ms para conexión
y lectura por solicitud). Solo configuración del servidor; no acepta URLs del cliente.

Se comprueban cobertura completa, pares esperados sin duplicados, fecha de origen
válida y no futura, y tasas positivas. Un error HTTP, timeout o respuesta inválida
produce 503 y conserva las tasas anteriores. El lote se guarda en una transacción;
los conflictos de unicidad producen 409. `fetchedAt` indica la hora UTC de consulta,
no la fecha de publicación del proveedor, que se valida pero no se persiste porque
el contrato actual de BD no incluye una columna para ella.

Las tasas se redondean con HALF_EVEN a los seis decimales de NUMERIC(16,6).
Se rechazan valores que se conviertan en cero o excedan la capacidad de la columna.
Esto limita la precisión de pares pequeños como COP/USD; son conversiones
orientativas, no cotizaciones para liquidar pagos. Frankfurter ofrece tasas
combinadas de referencia, no garantiza la TRM oficial colombiana. Condiciones de
las fuentes: https://frankfurter.dev/license/.

## Verificación y estado

`./mvnw verify` ejecuta pruebas MockMvc sobre PostgreSQL 17 temporal: lectura pública,
restricciones 401/403, escritura administrativa, validación, duplicados, caché y
sincronización mediante un proveedor simulado. Solo los contenedores de prueba
generan su esquema con Hibernate; ningún perfil de ejecución crea tablas.

Resultado local: 46 pruebas, 0 fallos, 0 errores. El adaptador se prueba con un servidor HTTP local: pares completos, respuesta inválida, HTTP 429 y timeout; PostgreSQL verifica persistencia, redondeo y conservación de tasas ante errores. Se verificó el arranque con `validate` contra una instancia temporal migrada
externamente desde el repositorio `database` para contrastar los tipos reales. Las consultas públicas devolvieron HTTP 200: 5 idiomas, 3 monedas y ninguna tasa inicial. La instancia temporal se eliminó al terminar.

La integración del proveedor está implementada. Falta publicar los
cambios, verificar CI y revisar el PR de `HU-BE-03-dev` hacia `dev`.
