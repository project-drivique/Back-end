# HU-BE-17 — Catálogo y consulta pública de flota con filtros de disponibilidad

Consume la tabla `fleet.vehicles` estructurada en HU-BD-19 y sus tablas relacionadas
(marcas, categorías, transmisiones, combustibles, estados y sedes). Implementa el motor
de búsqueda con filtrado dinámico mediante Spring Data JPA Specifications.

## Endpoints

* `GET /api/v1/vehicles/search`: Público. Búsqueda y filtrado dinámico paginado. Soporta parámetros:
  * `branchId`: Identificador UUID de la sede física actual del vehículo.
  * `categoryId`: Identificador UUID del segmento/categoría (SUV, Sedán, etc.).
  * `transmissionId`: Identificador UUID del tipo de transmisión (MANUAL, AUTOMATIC).
  * `fuelId`: Identificador UUID del tipo de combustible (GASOLINE, DIESEL, ELECTRIC, HYBRID).
  * `minPrice` / `maxPrice`: Rango de tarifa diaria en COP.
  * `featured`: Filtro por vehículos destacados en portada (`true` / `false`).
  * `page`, `size`, `sort`: Parámetros de paginación y ordenamiento (ej. `dailyRate,asc`).
  * *Solo retorna vehículos con `is_active = true`, `status.allows_reservation = true` y `status.is_active = true`.*
* `GET /api/v1/vehicles/featured`: Público. Retorna la lista de vehículos activos y disponibles marcados como destacados (`is_featured = true`), ordenados por tarifa diaria ascendente.
* `GET /api/v1/vehicles/{id}`: Público. Consulta la ficha técnica y datos comerciales de un vehículo por su identificador UUID.
