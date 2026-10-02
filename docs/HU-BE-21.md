# HU-BE-21 — Mantenimientos de flota

Usa las tablas `fleet.maintenance_types` y `fleet.vehicle_maintenances` del repositorio
`database` (HU-BD-22). No contiene SQL ni migraciones en este repositorio.

Los endpoints administrativos requieren `fleet:manage`, `ADMIN` o `SUPER_ADMIN`:

- `POST /api/v1/admin/maintenances`: programa el mantenimiento y cambia el vehículo a `MAINTENANCE`.
- `PATCH /api/v1/admin/maintenances/{id}/complete`: registra fecha y costo final, y restaura `AVAILABLE`.
- `GET /api/v1/admin/vehicles/{id}/maintenances`: consulta el historial ordenado por fecha programada.
