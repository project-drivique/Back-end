# HU-BE-06 — Autenticación JWT: Inicio de sesión, Bloqueo de cuenta y Revocación de Tokens

## Resumen de Implementación

Se ha implementado el módulo completo de autenticación de Drivique según los requerimientos de la historia de usuario **HU-BE-06**:

1. **Modelado y Entidades JPA:**
   - `Role` (`iam.roles`): Mapeo de roles del sistema (SUPER_ADMIN, BRANCH_ADMIN, EMPLOYEE, CUSTOMER).
   - `User` (`iam.users`): Manejo de credenciales con BCrypt, contador de intentos fallidos (`failed_login_attempts`), bloqueo temporal (`locked_until`), roles asignados y estados de cuenta.
   - `UserSession` (`iam.user_sessions`): Trazabilidad de sesiones activas, almacenamiento seguro de hash de refresh token (SHA-256), IP, dispositivo, expiración y revocación.

2. **Servicio JWT y Criptografía (`JwtService`):**
   - Emisión de Access Token JWT (15 minutos) firmado con HMAC-SHA256 conteniendo claims: `sub` (ID de usuario), `email`, `name`, `roles`.
   - Generación criptográfica y rotación de Refresh Tokens (7 días).
   - Hash unidireccional SHA-256 para persistencia segura de refresh tokens en base de datos.
   - Validación y extracción segura de claims sin fuga de información sensible.

3. **Lógica de Autenticación y Bloqueo (`AuthService`):**
   - `login`: Valida email y clave; ante fallo incrementa `failed_login_attempts`. Al 5to intento fallido bloquea la cuenta por 30 minutos (`locked_until = now() + 30m`).
   - Retorna HTTP `423 Locked` con formato RFC 7807 cuando la cuenta se encuentra bloqueada.
   - `refresh`: Valida la sesión activa y aplica rotación obligatoria de refresh token (revoca el anterior y genera un nuevo par access + refresh token). Rechaza tokens revocados o expirados con HTTP `401 Unauthorized`.
   - `logout`: Revoca el refresh token activo en `user_sessions`.

4. **Filtro de Seguridad y Control de Acceso (`JwtAuthenticationFilter` & `SecurityConfig`):**
   - Interceptor `OncePerRequestFilter` que valida el header `Authorization: Bearer <token>` y propaga las autoridades (`ROLE_<ROLE>`) al `SecurityContextHolder`.
   - Endpoints públicos: `/api/v1/auth/login`, `/api/v1/auth/refresh`, documentación OpenAPI y catálogos públicos.
   - Endpoints protegidos: `/api/v1/auth/logout` y operaciones administrativas (e.g. `@PreAuthorize("hasRole('SUPER_ADMIN')")`).

5. **Pruebas Automatizadas:**
   - Pruebas unitarias de hashing y ciclo de vida de tokens (`JwtServiceTests`).
   - Pruebas unitarias completas de lógica de negocio, bloqueo por fuerza bruta, rotación y revocación (`AuthServiceTests`).
   - Pruebas de integración MockMvc con soporte para base de datos (`AuthIntegrationTests`).
