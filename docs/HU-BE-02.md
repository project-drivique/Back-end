# HU-BE-02 — OpenAPI y manejo global de errores

## Alcance

Configurar la documentación de Drivique y unificar errores de MVC y Spring Security.
Esta HU no implementa login, JWT ni endpoints de negocio, y no modifica la BD.

## Documentación de API

- Swagger UI: `GET /api/swagger-ui.html` (redirige a `/api/swagger-ui/index.html`).
- Contrato OpenAPI 3.0: `GET /api/v3/api-docs`.
- Título: `Drivique API`; versión obtenida de Maven.
- Componentes reutilizables: `ApiProblem`, `ValidationError` y respuestas
  `Error400`, `Error401`, `Error403`, `Error404`, `Error409`, `Error500`.

Los endpoints anteriores son públicos. Los demás conservan el requisito de
autenticación. Las próximas HU deben documentar sus operaciones con `@Operation`,
DTO con `@Schema` y respuestas con `@ApiResponse`, referenciando estos componentes.
No se declara un mecanismo JWT operativo antes de implementarlo.

## Contrato de error

Content-Type: `application/problem+json`. Ejemplo de un recurso inexistente:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "El recurso solicitado no existe.",
  "instance": "/api/v1/resources/example",
  "timestamp": "2026-09-28T16:00:00Z",
  "errors": []
}
```

`about:blank` identifica el problema mediante el estado HTTP; `title` usa su nombre
estándar. `timestamp` y `errors` son extensiones. `instance` contiene la ruta sin
parámetros de consulta. Los errores de Bean Validation incluyen `field`, `code` y
un mensaje seguro, nunca el valor rechazado ni mensajes interpolados con ese valor.

| Origen | HTTP |
| --- | --- |
| Validación de DTO, JSON inválido | 400 |
| Credenciales inválidas o solicitud protegida sin autenticación | 401 |
| Acceso denegado | 403 |
| `ResourceNotFoundException` o ruta inexistente para un usuario autenticado | 404 |
| `ConflictException` | 409 |
| Error inesperado | 500 |

Los errores propios de MVC conservan su estado (por ejemplo 405 o 415) y sus
headers relevantes. Un error inesperado se registra en el servidor y devuelve un
mensaje genérico. No se envían al cliente trazas ni mensajes internos de excepciones.

`ApiExceptionHandler` cubre controladores y errores MVC; `ProblemSecurityHandler`
aplica el mismo contrato en los filtros de seguridad, antes del controlador.
Las excepciones de dominio aceptan mensajes internos, pero estos no se publican.

## Validación local

Ejecutar `.\mvnw.cmd clean verify` con Java 21 y Docker activo.

Resultado local: 28 pruebas, 0 fallos y 0 errores. Incluye MockMvc para errores,
validación, JSON inválido, método no permitido, rutas inexistentes, acceso anónimo,
Swagger y contrato OpenAPI, más las pruebas de conexión a PostgreSQL de HU-BE-01.
Los controladores que fuerzan errores existen solo en los archivos de prueba.

Pendientes de cierre: commit/push, ejecución de GitHub Actions y revisión del PR
`HU-BE-02-dev -> dev`. La promoción a QA y main se realiza mediante ramas hijas.
