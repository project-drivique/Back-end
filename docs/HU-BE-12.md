# HU-BE-12 — División territorial

La implementación consume las tablas `location.departments` y `location.cities`
del repositorio `database` (HU-BD-14). Este repositorio no contiene SQL ni
migraciones de esas tablas.

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET /api/v1/departments` | Público | Departamentos activos ordenados por nombre |
| `GET /api/v1/cities` | Público | Ciudades activas, con banderas de aeropuerto y terminal |
| `GET /api/v1/departments/{id}/cities` | Público | Ciudades activas del departamento activo |
| `POST /api/v1/cities` | `BRANCH_ADMIN` o `SUPER_ADMIN` | Crea una ciudad |
| `PUT /api/v1/cities/{id}` | `BRANCH_ADMIN` o `SUPER_ADMIN` | Actualiza una ciudad |

Los listados de ciudades se almacenan en la caché de aplicación durante cinco
minutos. Crear o actualizar una ciudad invalida ambas vistas en caché. Los nombres
son únicos por departamento y solo se permite asociar una ciudad a un departamento
activo.
