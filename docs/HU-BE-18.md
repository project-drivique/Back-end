# HU-BE-18 — Administración de Vehículos (CRUD de Flota, Placas y VIN)

Consume la tabla `fleet.vehicles` estructurada en HU-BD-19 y sus tablas relacionadas
(marcas, categorías, transmisiones, combustibles, estados y sedes). Implementa los
endpoints administrativos protegidos para el alta, edición de kilometraje, cambio de sede,
actualización de estado operativo e inactivación lógica de automóviles.

## Endpoints

* `GET /api/v1/admin/vehicles`: Protegido (`fleet:manage` / `ADMIN` / `SUPER_ADMIN`). Listado paginado de toda la flota con datos administrativos completos.
* `GET /api/v1/admin/vehicles/{id}`: Protegido. Detalle técnico y comercial de un vehículo específico.
* `POST /api/v1/admin/vehicles`: Protegido. Registro de nuevo automóvil en flota con validación de placa (`^[A-Z0-9-]+$`), unicidad de VIN (17 caracteres) y asignación de sede inicial.
* `PUT /api/v1/admin/vehicles/{id}`: Protegido. Actualización de kilometraje (con regla de negocio `mileage >= current_mileage`), tarifa diaria base y sede física actual.
* `PATCH /api/v1/admin/vehicles/{id}/status`: Protegido. Cambio de estado operativo del vehículo (`AVAILABLE`, `MAINTENANCE`, `OUT_OF_SERVICE`).
* `PATCH /api/v1/admin/vehicles/{id}/toggle-status`: Protegido. Alternar estado activo/inactivo (borrado lógico) del vehículo.
* `DELETE /api/v1/admin/vehicles/{id}`: Protegido. Inactivación lógica segura del vehículo preservando la integridad referencial histórica con contratos y reservas.
