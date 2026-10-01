# HU-BE-16 — Gestión de categorías de vehículos y tarifas base

Consume la tabla `fleet.vehicle_categories` estructurada en HU-BD-18. Expone los endpoints
para consultar y administrar las categorías vehiculares, tarifas diarias base de referencia
y depósitos de garantía.

## Endpoints

* `GET /api/v1/vehicle-categories`: Público. Retorna el listado de categorías activas con sus tarifas y depósitos, ordenadas alfabéticamente por nombre. Respuestas cacheadas por 5 minutos en `vehicleCategories`.
* `GET /api/v1/vehicle-categories/{id}`: Público. Consulta el detalle de una categoría por su identificador único UUID.
* `POST /api/v1/vehicle-categories`: Protegido con rol `SUPER_ADMIN`. Crea una nueva categoría validando nombre no vacío y único, `base_daily_rate >= 0` y `security_deposit >= 0`. Invalida la caché `vehicleCategories`.
* `PUT /api/v1/vehicle-categories/{id}`: Protegido con rol `SUPER_ADMIN`. Modifica tarifas, depósito o nombre de la categoría validando unicidad e integridad. Invalida la caché `vehicleCategories`.
* `PATCH /api/v1/vehicle-categories/{id}/toggle-status`: Protegido con rol `SUPER_ADMIN`. Alterna la activación/inactivación lógica (`is_active`) de la categoría. Invalida la caché `vehicleCategories`.
