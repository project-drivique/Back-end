# HU-BE-19 — Galería Multimedia y Documentación Técnica de Vehículos

Consume las tablas `fleet.vehicle_images` y `fleet.vehicle_documents` estructuradas en HU-BD-20.
Implementa los endpoints administrativos protegidos para gestionar la galería fotográfica
de los automóviles y auditar la vigencia de documentos legales (SOAT, Revisión Técnico-Mecánica, Tarjeta de Propiedad, Pólizas de Seguro).

## Endpoints

### Galería Fotográfica
* `GET /api/v1/admin/vehicles/{id}/images`: Protegido (`fleet:manage` / `ADMIN` / `SUPER_ADMIN`). Lista todas las fotos del vehículo ordenadas por `sort_order`.
* `POST /api/v1/admin/vehicles/{id}/images`: Protegido. Agrega una nueva imagen con URL, orden (`sort_order`) y bandera de foto principal (`is_primary`).
* `PATCH /api/v1/admin/vehicles/{id}/images/{imageId}/primary`: Protegido. Marca una imagen existente como foto principal y actualiza la miniatura principal del vehículo.
* `DELETE /api/v1/admin/vehicles/{id}/images/{imageId}`: Protegido. Elimina la foto de la galería del vehículo.

### Documentación Técnica y Legal
* `GET /api/v1/admin/vehicles/{id}/documents`: Protegido. Lista todos los documentos activos del vehículo.
* `POST /api/v1/admin/vehicles/{id}/documents`: Protegido. Registra o renueva un documento (`SOAT`, `TECHNICAL_INSPECTION`, `REGISTRATION`, `INSURANCE`, `OTHER`) con fechas de expedición y vencimiento.
* `GET /api/v1/admin/vehicles/{id}/documents/expiring`: Protegido. Alerta de documentos del vehículo próximos a vencer ($\le$ 30 días o vencidos).
* `GET /api/v1/admin/vehicles/documents/expiring`: Protegido. Alerta global consolidada de todos los documentos de la flota próximos a vencer.
* `DELETE /api/v1/admin/vehicles/{id}/documents/{documentId}`: Protegido. Inactiva lógicamente un documento del vehículo.
