# HU-BE-05 — Registro y políticas de contraseñas (en progreso)

La base de código se organizó en el módulo `auth`, separado por `entity`, `repository`,
`service`, `dto`, `mapper` y `controller`. Consultar `docs/architecture.md`.

## Implementado

- Mapeo de `iam.password_policies` según el repositorio database, commit
  `d1e70cb592cb7c3be2eb1c3e683562353ad9043a`.
- `PasswordValidatorService`: consulta la política activa en cada validación,
  comprueba longitud en caracteres Unicode, mayúsculas, números ASCII y símbolos.
  Espacios o controles no cuentan como símbolos. No recorta ni transforma la clave.
- Límite de 72 bytes UTF-8 antes de BCrypt. Rechaza claves excesivas sin truncarlas.
- `PasswordEncoder` con BCrypt y costo 12, con sal aleatoria por hash.
- Política ausente, múltiple o longitud incompatible: 503. Contraseña inválida: 400.
  No se incluyen contraseñas en mensajes de error.

## Dependencia por resolver

Al revisar las ramas remotas main y dev de `project-drivique/database`, no están
las migraciones de users, user_roles, roles ni verification_codes. El script antiguo
`database_drivique.sql` contiene estructuras preliminares, pero diverge del contrato
actual: utiliza EMAIL_VERIFICATION y la HU exige ACCOUNT_VERIFICATION.
También exige document_type_id y referencia nationalities, contratos que deben confirmarse.

Falta recibir o localizar la rama/archivo aprobado de esas tablas para implementar
el registro transaccional, duplicados 409, rol CUSTOMER y emisión/persistencia del OTP.
La entrega por correo requerirá definir el transporte; no se expondrán OTP ni hashes
mediante la respuesta pública ni logs. No se han creado tablas ni migraciones en backend.
El endpoint de registro todavía no está implementado. Login y tokens JWT corresponden a HU-BE-06.
