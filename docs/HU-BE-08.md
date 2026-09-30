# HU-BE-08 — Gestión de Perfil de Usuario y Preferencias

## Resumen de Implementación

Se ha implementado el módulo de gestión de perfiles de usuario y preferencias de configuración y notificaciones de acuerdo a los requerimientos de la historia **HU-BE-08**:

1. **Modelado y Persistencia JPA:**
   - `UserPreference` (`iam.user_preferences`): Entidad con relación `@OneToOne` mapeada a la tabla `iam.users`, con atributos `language_id`, `currency_id`, `theme_preference` (`LIGHT`, `DARK`, `SYSTEM`), `email_notifications` y `sms_notifications`.
   - `UserPreferenceRepository`: Repositorio Spring Data JPA para consultar y persistir preferencias de usuario.

2. **Lógica de Negocio y Servicios:**
   - `UserService`:
     - `getProfile`: Consulta y retorna el perfil detallado del usuario a partir de su identidad en el token JWT.
     - `updateProfile`: Permite la modificación de atributos seguros (`firstName`, `lastName`, `phone`, `birthDate`, `nationalityId`) recalculando el estado de completitud del perfil (`is_profile_complete`), protegiendo `email` y `document_number` contra modificaciones directas.
   - `UserPreferenceService`:
     - `getPreferences`: Obtiene las preferencias del usuario o inicializa y persiste un registro por defecto si aún no existía.
     - `updatePreferences`: Actualiza selectivamente idioma, moneda, tema y canales de notificación.

3. **Capa REST y Documentación OpenAPI (`UserController`):**
   - `GET /v1/users/me`: Consulta de perfil propio autenticado.
   - `PUT /v1/users/me`: Actualización de perfil con validaciones Jakarta.
   - `GET /v1/users/me/preferences`: Consulta de preferencias.
   - `PUT /v1/users/me/preferences`: Actualización de preferencias.
   - Acceso securizado mediante `SecurityConfig` (requiere JWT Bearer).

4. **Pruebas Automatizadas:**
   - `UserServiceTests`: Pruebas unitarias de consulta y actualización de perfil, validación de integridad y campos no mutables.
   - `UserPreferenceServiceTests`: Pruebas unitarias de inicialización por defecto y actualización de preferencias.
   - `UserIntegrationTests`: Pruebas de integración MockMvc con autenticación JWT contra base de datos.
