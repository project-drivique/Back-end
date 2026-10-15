# HU-INT-20 · Inventario de datos simulados y fuentes locales

Clasificación de lo encontrado en Backend, Web y App, y qué se hizo con cada elemento.

## Se eliminó (era simulación o un riesgo de seguridad)

| Repo | Elemento | Por qué | Resultado |
|---|---|---|---|
| Backend | Tokens `sandbox_google_token:*`, `sandbox_fb_token:*`, `mock_*` en `OAuthProviderService` | Permitían iniciar sesión como cualquier correo | Eliminados; el proveedor real (Google/Facebook) se conserva |
| Backend | Lectura de un JWT de Google sin verificar la firma, y correo enviado por el cliente cuando Facebook no respondía | Falsificación de identidad | Eliminados |
| Backend | `MockEmailProvider` (simulaba envíos exitosos) | Marcaba como enviado un correo que nunca salió | Ahora falla de forma explícita si no hay servidor de correo |
| Backend | `ClauseService.getOrSeedDefaultClauses` y `seedReportTypesIfEmpty` | Datos de negocio creados desde el código | Las cláusulas pasan a una migración (`hu-int-20.yaml`); los tipos de reporte ya venían de migraciones |
| Web | `VITE_USAR_MOCK`, `USAR_MOCK` y el filtrado local de `catalogService` | Modo productivo con datos locales | Eliminados |
| Web | `VITE_MOCK_*` en `.env.example` | Usuarios demo | Eliminados |
| Web / App | Tokens `sandbox_*` y correo fijo de la desarrolladora como respaldo de Google/Facebook | Sesiones de prueba | Eliminados; ahora se muestra un error claro |
| App | `'mock_token_google'` como token por defecto | Sesión simulada | Eliminado |

## Se conserva (no es dato de negocio simulado)

| Repo | Elemento | Motivo |
|---|---|---|
| Web | `src/config/datos/*.json` (antes `src/mocks`) | Configuración estática de interfaz (filtros, metadatos de ciudades, país, marca de arranque). Se renombró para que ninguna ruta diga `mocks` |
| Web / App | Catálogos de soporte (canales, tipos de incidencia, preguntas frecuentes) | Contenido informativo; la App lo renombró de `support.dummy` a `supportCatalog` |
| Web / App | Cachés locales (marca, tasa de cambio, sede activa, tema, idioma) | Preferencias o cachés con recuperación desde el backend |

## Regla automática

- **Backend:** el paso "Sin datos ni proveedores simulados" de `ci.yml` falla si `src/main` contiene proveedores o semillas simuladas.
- **Web y App:** `scripts/check-no-simulated-data.mjs` (workflow `no-simulated-data.yml`) falla si el código productivo importa rutas `mocks`, `dummy` o `fixtures`, usa `USAR_MOCK` o tokens de proveedores simulados. Pruebas (`*.test.*`, `__tests__`, `__mocks__`) y Storybook quedan fuera del escaneo.

## Pendiente de revisión (cachés)

Las cachés de la Web (`drivique_admin_branches`, sede activa) y de la App (marca, tasa de cambio) se recargan desde el backend al iniciar; el detalle de TTL se documenta en cada servicio.
