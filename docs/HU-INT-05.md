# HU-INT-05 | Inicio de sesión social seguro con Google y Facebook

## Descripción
Implementación de inicio de sesión e integración OAuth 2.0 / OIDC para Google y Facebook, validación de Authorization Code con PKCE, auto-registro o vinculación a cuentas existentes, y emisión de tokens propios JWT de Drivique.

## Endpoints
* `POST /api/v1/auth/social/login`: Autenticación con Google o Facebook mediante PKCE / ID Token.
* `POST /api/v1/auth/google`: Endpoint rápido para Google Identity Services.
* `POST /api/v1/auth/facebook`: Endpoint rápido para Facebook SDK.
* `POST /api/v1/auth/social/link`: Vinculación de proveedor al usuario autenticado.
* `GET /api/v1/auth/social/accounts`: Listado de proveedores vinculados.
* `DELETE /api/v1/auth/social/{provider}`: Desvinculación de proveedor.
