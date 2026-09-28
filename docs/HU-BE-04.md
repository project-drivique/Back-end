# HU-BE-04 — Configuración de marca, tema y parámetros globales

## Contrato

Se usan `core.brand_configurations` e `iam.security_configurations` del repositorio
`project-drivique/database` (referencia d1e70cb592cb7c3be2eb1c3e683562353ad9043a).
Las migraciones y semillas siguen exclusivamente en ese repositorio.
Hibernate conserva `ddl-auto: validate` en ejecución.

## API

| Ruta bajo /api | Acceso | Comportamiento |
| --- | --- | --- |
| GET /v1/brand-configurations/active | Público | Marca activa; 404 si no existe |
| PUT /v1/brand-configurations | SUPER_ADMIN | Reemplaza los campos editables de la marca activa; 404 si no existe |
| GET /v1/security-configurations | Público | Lista explícita de parámetros no sensibles |

Ejemplo de PUT:

```json
{
  "companyName": "Drivique",
  "logoUrl": "https://example.com/logo.png",
  "faviconUrl": null,
  "primaryColor": "#2563EB",
  "secondaryColor": "#1E3A8A",
  "accentColor": "#60A5FA",
  "defaultTheme": "SYSTEM"
}
```

Los tres colores deben cumplir `^#[0-9A-Fa-f]{6}$`, ser distintos sin importar
mayúsculas y se almacenan en mayúsculas. El nombre es obligatorio (máximo 100).
Las URLs son opcionales (`null`), máximo 2048, y deben usar HTTP/HTTPS.
No se descargan imágenes ni se reciben archivos en este endpoint.

`defaultTheme` admite `LIGHT`, `DARK` y `SYSTEM`. El backend persiste y devuelve
el tema predeterminado; el frontend aplica el aspecto claro, oscuro o la preferencia
del sistema. No se implementa interfaz visual ni preferencias individuales en esta HU.

Se actualiza la fila activa con bloqueo de escritura en una transacción. No se
crean marcas, no se cambia su estado y no se permite modificar su ID mediante el DTO.
La unicidad de la marca activa y los timestamps los administra la BD existente.
No se añade caché: la lectura pública refleja el último cambio confirmado.
Los errores siguen el contrato de HU-BE-02; Swagger describe solicitudes y respuestas.

## Parámetros públicos

La respuesta contiene exclusivamente `configKey` y `configValue`, ordenados por clave.
Se permiten `SESSION_IDLE_TIMEOUT_MINUTES` y
`PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES`, presentes en las semillas de BD, para
los tiempos visibles al cliente. Las claves ausentes se omiten; una lista vacía
no implica valores predeterminados ni desactiva controles del servidor.

No se exponen descripciones internas, IDs, umbrales de bloqueo, exigencias MFA,
secretos ni claves nuevas agregadas a la BD. Para publicar otra clave hay que
revisar y ampliar explícitamente la lista permitida. Estas opciones informan al
cliente; los módulos de autenticación deben aplicar sus propias reglas del lado servidor.

## Validación y cierre

Pruebas MockMvc con PostgreSQL 17 temporal: lectura de marca activa, ausencia,
actualización y persistencia, HEX en cada campo, colores repetidos, tema inválido,
URL insegura, nombre vacío, permisos 401/403, exclusión de claves internas y Swagger.
Solo las pruebas generan esquemas temporales; la aplicación requiere las migraciones externas.

El control SUPER_ADMIN está implementado y probado con el contexto de seguridad;
el login/JWT real corresponde a HU-BE-06.
Pendientes del cierre: commit/push, PR hacia dev, CI en verde y promoción por ambientes.

Resultado local: 61 pruebas, 0 fallos y 0 errores (mvnw verify). Se verificó el arranque con Hibernate validate contra PostgreSQL 17 temporal con las 24 migraciones del repositorio database; ambos GET públicos devolvieron la marca Drivique con tema SYSTEM y únicamente los dos parámetros permitidos. Contenedor y volumen temporales eliminados al terminar.
