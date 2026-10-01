# HU-BE-14 — Asignación de personal a sedes

Usa exclusivamente `location.branch_users` de HU-BD-16. La asignación tiene una
clave única compuesta por usuario y sede.

- `POST /api/v1/branches/{branchId}/staff/{userId}` asigna personal operativo.
- `DELETE /api/v1/branches/{branchId}/staff/{userId}` retira la asignación.
- `GET /api/v1/branches/{branchId}/staff` lista el personal y sus roles.

Los tres endpoints requieren la autoridad `branches:manage_staff` o los roles
`BRANCH_ADMIN` / `SUPER_ADMIN`. Solo pueden asignarse
usuarios con rol `EMPLOYEE`, `BRANCH_ADMIN` o `SUPER_ADMIN`.
