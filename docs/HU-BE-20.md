# HU-BE-20 — Equipamiento, Características y Vehículos Favoritos de Usuario

Consume las tablas `fleet.features`, `fleet.vehicle_features` y `fleet.user_favorite_vehicles` estructuradas en HU-BD-21.
Implementa el catálogo técnico de equipamiento/características vehiculares agrupadas por categoría (STANDARD, COMFORT, TECHNOLOGY, SECURITY, ACCESSORIES), la asociación administrativa de características a los vehículos de la flota, y la gestión de vehículos favoritos por parte de los usuarios autenticados.

## Endpoints

### Catálogo de Características y Equipamiento
* `GET /api/v1/features`: Público. Retorna el catálogo completo de características activas agrupadas por grupo (`STANDARD`, `COMFORT`, `TECHNOLOGY`, `SECURITY`, `ACCESSORIES`).
* `GET /api/v1/features/all`: Público. Retorna la lista plana de todas las características activas.
* `GET /api/v1/vehicles/{id}/features`: Público. Consulta todas las características y equipamiento asociados a un vehículo específico.
* `POST /api/v1/admin/vehicles/{id}/features`: Protegido (`fleet:manage` / `ADMIN` / `SUPER_ADMIN`). Asigna una lista de características a un vehículo.
* `DELETE /api/v1/admin/vehicles/{id}/features/{featureId}`: Protegido. Remueve una característica del equipamiento del vehículo.

### Vehículos Favoritos del Usuario
* `GET /api/v1/users/me/favorites`: Protegido (Autenticado vía JWT). Retorna la lista de vehículos marcados como favoritos por el usuario actual, incluyendo detalles de marca, modelo, tarifas y fotos.
* `POST /api/v1/users/me/favorites/{vehicleId}`: Protegido (Autenticado vía JWT). Agrega un vehículo a la lista de favoritos del usuario.
* `DELETE /api/v1/users/me/favorites/{vehicleId}`: Protegido (Autenticado vía JWT). Elimina un vehículo de la lista de favoritos del usuario.
