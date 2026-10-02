# HU-BE-29 — Gestión de Solicitudes de Extensión y Prórroga de Alquiler

**Story Points:** 3  
**Prioridad:** 🟡 Should have  
**Dominio:** `rental`  
**Ramas:** `HU-BE-29-dev`, `HU-BE-29-qa`, `HU-BE-29-main`

---

## 📌 Descripción del Módulo y Capacidad Entregada
Implementación integral de la gestión, cotización y dictamen administrativo de prórrogas y extensiones de tiempo de alquiler (`rental.rental_extension_requests`), permitiendo a los clientes solicitar extensiones de fechas de devolución (`POST /api/v1/reservations/{id}/extensions`), validando la ausencia de reservas posteriores en conflicto sobre el vehículo, y permitiendo a agentes y administradores dictaminar (`PATCH /api/v1/admin/extensions/{id}`) con aprobación (`APPROVED`) o rechazo (`REJECTED`), actualizando automáticamente la fecha de retorno y monto total de la reserva al aprobar.

---

## 🚀 Endpoints Implementados

| Método | Ruta | Seguridad | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/reservations/{id}/extensions` | `Bearer JWT (Cliente titular / Staff)` | Solicita extensión de alquiler con nueva fecha de devolución, validando disponibilidad y cotizando monto adicional |
| `GET` | `/api/v1/reservations/{id}/extensions` | `Bearer JWT (Cliente titular / Staff)` | Lista el historial de solicitudes de extensión asociadas a la reserva |
| `GET` | `/api/v1/admin/extensions` | `Bearer JWT (ADMIN, SUPER_ADMIN, EMPLOYEE, BRANCH_ADMIN)` | Lista todas las solicitudes de extensión con filtro opcional por estado (`status`) |
| `GET` | `/api/v1/admin/extensions/{id}` | `Bearer JWT (ADMIN, SUPER_ADMIN, EMPLOYEE, BRANCH_ADMIN)` | Consulta el detalle específico de una solicitud de extensión |
| `PATCH` | `/api/v1/admin/extensions/{id}` | `Bearer JWT (ADMIN, SUPER_ADMIN, EMPLOYEE, BRANCH_ADMIN)` | Dictamina la solicitud (`APPROVED` o `REJECTED`), actualizando `return_date` y total estimado en la reserva al ser aprobada |

---

## 🔒 Reglas de Negocio y Validaciones

1. **Cotización de Días Adicionales:**
   - La nueva fecha solicitada (`requested_return_date`) debe ser estrictamente posterior a la fecha de devolución actual (`return_date`).
   - El monto adicional se cotiza multiplicando la suma de las tarifas diarias vigentes (tarifa base del vehículo + seguro contratado + plan de kilometraje + servicios adicionales) por los días extras solicitados.
2. **Validación Anticolisión de Vehículo:**
   - Se valida que el vehículo no tenga ninguna reserva posterior activa (`blocks_availability = true`) solapada en el intervalo de la extensión solicitada (`existsOverlappingReservationExcluding`).
   - Si existe colisión, se rechaza la solicitud arrojando error HTTP 409 Conflict.
3. **Control de Duplicidad:**
   - No se permite radicar una nueva solicitud de extensión si ya existe una solicitud en estado `PENDING` para la misma reserva.
4. **Dictamen y Actualización Atómica:**
   - Al aprobar una solicitud (`APPROVED`), se revalida la disponibilidad del vehículo, se actualiza `return_date` en la reserva, se incrementa `total_estimated` con el valor adicional y se marca `reviewed_by` y `reviewed_at`.
   - Las solicitudes ya dictaminadas no pueden volver a ser procesadas.
