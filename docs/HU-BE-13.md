# HU-BE-13 — Sedes físicas y horarios

Usa `location.branches` del repositorio `database` (HU-BD-15), sin SQL ni
migraciones en el backend.

- `GET /api/v1/branches`, `GET /api/v1/branches/{id}` y `GET /api/v1/cities/{id}/branches` son públicos y solo exponen sedes activas.
- `POST`, `PUT` y `PATCH /api/v1/branches/{id}/toggle-status` requieren `BRANCH_ADMIN` o `SUPER_ADMIN`.
- La creación y actualización validan que `closingTime` sea posterior a `openingTime` y que la ciudad esté activa.
