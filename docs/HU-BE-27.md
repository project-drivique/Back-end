# HU-BE-27 — Puntos y Modalidades de Entrega/Devolución (Sede, Domicilio, Aeropuerto, Terminal)

**Story Points:** 3  
**Prioridad:** 🔴 Must have  
**Dominio:** `rental`  
**Ramas:** `HU-BE-27-dev`, `HU-BE-27-qa`, `HU-BE-27-main`

---

## 📌 Descripción del Módulo y Capacidad Entregada
Implementación completa de la gestión de puntos y modalidades de entrega (`PICKUP`) y devolución (`RETURN`) para reservas vehiculares (`/api/v1/reservations/{id}/delivery-points`), soportando modalidades de sucursal física (`BRANCH`), entrega a domicilio (`HOME_DELIVERY`), aeropuerto (`AIRPORT`) y terminal de transporte terrestre (`TERMINAL`) en Drivique.

---

## 🚀 Endpoints Implementados

| Método | Ruta | Seguridad | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/reservations/{id}/delivery-points` | `Bearer JWT (Propietario/Staff)` | Registra o actualiza en lote los puntos de entrega y devolución de la reserva |
| `GET` | `/api/v1/reservations/{id}/delivery-points` | `Bearer JWT (Propietario/Staff)` | Obtiene los puntos de entrega y devolución configurados para una reserva |
| `PUT` | `/api/v1/reservations/{id}/delivery-points/{pointType}` | `Bearer JWT (Propietario/Staff)` | Configura o actualiza un punto específico (`PICKUP` o `RETURN`) |

---

## 🔒 Reglas de Negocio y Validaciones por Modalidad

1. **Modalidad `BRANCH`:**
   - Vincula obligatoriamente una sede física activa (`branch_id`).
   - `city_id` y `address` se omiten (permanecen nulos).
2. **Modalidad `HOME_DELIVERY`:**
   - Requiere obligatoriamente `city_id` (ciudad activa) y `address` (dirección exacta no vacía).
   - Valida opcionalmente barrio/sector (`neighborhood`) y referencias.
   - `branch_id` permanece nulo.
3. **Modalidad `AIRPORT` / `TERMINAL`:**
   - Requiere `city_id` (ciudad donde se ubica la terminal o aeropuerto).
   - Almacena y valida número de vuelo o bus (`flight_or_bus_number`) y detalles de referencia (`reference_details`).
   - `branch_id` permanece nulo.
4. **Control de Acceso y Autorización:**
   - Únicamente el cliente titular de la reserva o usuarios con rol administrativo/agente (`ADMIN`, `SUPER_ADMIN`, `AGENT`) tienen autorización para configurar o consultar los puntos de entrega.
