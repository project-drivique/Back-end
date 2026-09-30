# HU-BE-07 — Verificación de Cuentas, 2FA y Recuperación de Contraseña (Códigos OTP)

## Resumen de Implementación

Se ha implementado el módulo completo de verificación de cuentas y recuperación de credenciales mediante códigos OTP de un solo uso para Drivique, cumpliendo estrictamente los requerimientos de **HU-BE-07**:

1. **Modelado y Persistencia JPA:**
   - `VerificationCode` (`iam.verification_codes`): Mapeo de códigos de verificación OTP asociados a usuarios con propósito (`ACCOUNT_VERIFICATION`, `PASSWORD_RESET`, etc.), expiración (`expires_at`), marca de uso (`used_at`) y hash criptográfico (`code_hash`).
   - `VerificationCodeRepository`: Consultas optimizadas con índices para búsqueda por usuario, propósito y hash del código.

2. **Servicio de Códigos OTP (`VerificationCodeService`):**
   - Generación criptográficamente segura de códigos numéricos de 6 dígitos mediante `SecureRandom`.
   - Almacenamiento seguro usando hashing unidireccional SHA-256 (nunca se almacena el código OTP en texto plano).
   - Validación y consumo atómico (`used_at = now()`), garantizando uso único e impidiendo ataques de repetición (*replay attacks*) o uso de códigos expirados.

3. **Flujos de Autenticación y Recuperación (`AuthService`):**
   - `verifyEmail`: Valida el código OTP de 6 dígitos con propósito `ACCOUNT_VERIFICATION`. Tras la validación, establece `email_verified_at = now()` y activa la cuenta del usuario (`account_status = 'ACTIVE'`).
   - `forgotPassword`: Genera un código OTP de 6 dígitos con vigencia de 15 minutos para `purpose = 'PASSWORD_RESET'`. Responde de forma segura y uniforme para evitar enumeración de cuentas.
   - `resetPassword`: Valida el código OTP, comprueba que la nueva contraseña cumpla con las políticas de seguridad activas (`PasswordValidatorService`), actualiza el hash de la contraseña (`BCryptPasswordEncoder` costo 12), restablece los intentos fallidos e invalida inmediatamente todas las sesiones activas del usuario (`UserSessionRepository.revokeAllActiveSessionsForUser`).

4. **Capa REST y Documentación OpenAPI (`AuthController` & `SecurityConfig`):**
   - `POST /v1/auth/verify-email`: Validación de DTO y retorno de `MessageResponseDTO`.
   - `POST /v1/auth/forgot-password`: Generación de OTP de restablecimiento.
   - `POST /v1/auth/reset-password`: Restablecimiento de contraseña y revocación de sesiones.
   - Configuración en `SecurityConfig` para permitir acceso público a estos endpoints con manejo de errores RFC 7807.

5. **Pruebas Automatizadas:**
   - `VerificationCodeServiceTests`: Pruebas unitarias de generación, expiración, consumo único y rechazo de códigos inválidos.
   - `AuthServiceTests`: Pruebas unitarias completas de verificación de correo, solicitud de recuperación y reseteo de contraseña con revocación de sesiones.
   - `AuthIntegrationTests`: Pruebas de integración MockMvc con PostgreSQL Testcontainers validando los flujos E2E de verificación, recuperación, reseteo de clave y rechazo de refresh tokens previos.
