# HU-BE-26 — Creación de Reservas y Bloqueo de Disponibilidad de Vehículo

**Story Points:** 5  
**Prioridad:** 🔴 Must have  
**Dominio:** `rental`  
**Ramas:** `HU-BE-26-dev`, `HU-BE-26-qa`, `HU-BE-26-main`

---

## 📌 Descripción del Módulo y Capacidad Entregada
Implementación completa del servicio, repositorios, controladores y pruebas para la creación de reservas vehiculares bajo transacción `@Transactional`, con bloqueo pesimista en tiempo real contra sobreventa (overbooking), generación de consecutivo amigable (`RES-YYYY-XXXX`), fijación de plazos de pago en efectivo y congelamiento de tarifas históricas en base de datos PostgreSQL 17.

---

## 🚀 Endpoints Implementados

| Método | Ruta | Seguridad | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/reservations` | `Bearer JWT (Cliente/Admin)` | Crea una reserva, bloquea calendario y congela precios |
| `GET` | `/api/v1/reservations/me` | `Bearer JWT (Cliente)` | Lista el historial de reservas del usuario autenticado |
| `GET` | `/api/v1/reservations/{id}` | `Bearer JWT (Propietario/Staff)` | Obtiene el detalle completo de una reserva por su UUID |
| `GET` | `/api/v1/reservations/code/{code}` | `Bearer JWT (Propietario/Staff)` | Consulta una reserva por su código de negocio |

---

## 🔒 Reglas de Negocio y Control de Concurrencia
1. **Bloqueo Pesimista (`LockModeType.PESSIMISTIC_WRITE`):** Previene condiciones de carrera al solicitar la misma unidad vehicular de manera concurrente.
2. **Validación de Disponibilidad en Tiempo Real:** Verifica que no existan reservas con `blocks_availability = true` en el rango `[pickup_date, return_date]`.
3. **Generación de Código Consecutivo:** Consecutivos anuales con formato `RES-YYYY-%04d` (ej: `RES-2026-0001`).
4. **Estado Inicial y Expiración:** Asigna estado `PENDING_PAYMENT` y calcula vencimiento de pago en efectivo (`min(now + 72h, pickup_date)`).
5. **Congelamiento de Tarifas:** Almacena copias inmutables de las tarifas de vehículo, seguro, plan de kilometraje, servicios adicionales en `rental.reservation_additional_services` y descuentos aplicados en `rental.reservation_promotions`.
