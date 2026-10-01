# HU-BE-11 — Consentimientos legales y trazabilidad de Habeas Data

La implementación usa exclusivamente `iam.user_consents`, entregada por HU-BD-13
en el repositorio `database`. El backend no incluye SQL ni migraciones.

## Endpoints autenticados

| Ruta | Resultado |
| --- | --- |
| `POST /api/v1/consents` | Registra tipo y versión para el usuario del JWT; devuelve 201 |
| `GET /api/v1/users/me/consents` | Devuelve el historial propio, del más reciente al más antiguo |
| `GET /api/v1/consents/status` | Indica qué versiones legales requeridas siguen pendientes |

La solicitud de registro recibe `consentType` y `documentVersion`. No recibe
`userId`, fecha ni IP: estos datos se determinan en el servidor. Cada combinación
usuario/tipo/versión se registra una sola vez; un intento repetido devuelve 409.
La tabla impide actualizar o borrar el registro ya creado.

La IP se toma de `HttpServletRequest.getRemoteAddr()` y se persiste como PostgreSQL
`INET`, incluidos IPv4 e IPv6. No se confía en `X-Forwarded-For` ni en cabeceras
similares aportadas por el cliente. El balanceador debe conservar la dirección remota
correcta en el entorno de despliegue.

## Versiones vigentes

La tabla de BD no contiene un catálogo de documentos legales vigentes. Por esa razón,
las versiones obligatorias se configuran por ambiente bajo `compliance.required-consents`.
El endpoint de estado devuelve `requirementsConfigured: false` mientras la lista esté
vacía; no afirma que el usuario esté al día cuando no existe una fuente de verdad.

Ejemplo de configuración:

```yaml
compliance:
  required-consents:
    - consent-type: TERMS_AND_CONDITIONS
      document-version: 2026-09
    - consent-type: PRIVACY_POLICY
      document-version: 2026-09
```
