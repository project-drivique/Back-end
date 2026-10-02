# HU-BE-28 — Job Automatizado de Expiración de Reservas a las 72 Horas

**Story Points:** 3  
**Prioridad:** 🔴 Must have  
**Dominio:** `rental` / `jobs`  
**Ramas:** `HU-BE-28-dev`, `HU-BE-28-qa`, `HU-BE-28-main`

---

## 📌 Descripción del Módulo y Capacidad Entregada
Implementación de la tarea programada periódica (`@Scheduled`) y servicio transaccional atómico en Spring Boot para la expiración y cancelación automática de reservas impagas cuyo plazo de 72 horas (o fecha límite de pago en efectivo) haya expirado, liberando de inmediato la disponibilidad del vehículo en Drivique, notificando al cliente y registrando el evento en la bitácora de auditoría.

---

## 🚀 Componentes y Endpoints Implementados

| Componente / Ruta | Tipo / Método | Seguridad / Programación | Descripción |
| :--- | :--- | :--- | :--- |
| `ReservationExpirationJob` | `@Scheduled` cron | `0 */5 * * * *` (Configurable vía `${app.jobs.reservation-expiration.cron}`) | Job automatizado que ejecuta cada 5 minutos buscando y cancelando reservas pendientes vencidas. |
| `ReservationExpirationService` | Service `@Transactional` | Inyección interna | Consulta reservas con estado `PENDING_PAYMENT` y `cash_payment_expires_at < now()`, pasa estado a `CANCELLED_BY_TIMEOUT`, libera bloqueo de disponibilidad (`blocksAvailability = false`), envía notificación y registra bitácora. |
| `POST /api/v1/admin/reservations/expire-pending` | Endpoint REST | `Bearer JWT (ADMIN, SUPER_ADMIN, EMPLOYEE, BRANCH_ADMIN)` | Endpoint administrativo para disparar manualmente el proceso de expiración bajo demanda. |

---

## 🔒 Reglas de Negocio y Transaccionalidad

1. **Criterio de Expiración:**
   - Consulta todas las reservas con estado `PENDING_PAYMENT` cuya fecha `cash_payment_expires_at` sea estrictamente menor al instante actual (`Instant.now()`).
2. **Transición de Estado y Liberación de Vehículo:**
   - La reserva pasa al estado `CANCELLED_BY_TIMEOUT`.
   - Se actualiza explícitamente `blocksAvailability = false` en la reserva, liberando de inmediato el rango de fechas en la consulta de solapamiento (`existsOverlappingReservation`).
3. **Notificación al Cliente y Auditoría:**
   - Se invoca `ReservationNotificationService` para emitir la notificación de expiración al cliente titular.
   - Se registra el evento detallado en la bitácora de auditoría (SLF4J `AUDIT: Reservation '...' has been CANCELLED_BY_TIMEOUT. Vehicle availability released.`).
4. **Resiliencia y Concurrencia:**
   - Ejecución atómica bajo `@Transactional`.
   - Si no existen reservas vencidas, finaliza de forma segura retornando contador en cero sin operaciones adicionales en base de datos.
