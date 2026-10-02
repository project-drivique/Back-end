# HU-BE-30 — Generación y Consulta de Contratos Legales de Arrendamiento

**Story Points:** 4  
**Prioridad:** 🔴 Must have  
**Dominio:** `contract`  
**Ramas:** `HU-BE-30-dev`, `HU-BE-30-qa`, `HU-BE-30-main`

---

## 📌 Descripción del Módulo y Capacidad Entregada
Implementación de los servicios y endpoints para la emisión automatizada de contratos legales de arrendamiento vehicular (`POST /api/v1/contracts/generate`), consulta pública de cláusulas legales vigentes (`GET /api/v1/contracts/clauses`), y consulta de detalle de contratos emitidos (`GET /api/v1/contracts/{id}`, `GET /api/v1/contracts/reservation/{reservationId}`, `GET /api/v1/contracts/number/{contractNumber}`) en Drivique, asegurando un identificador consecutivo único (`CTR-YYYY-XXXX`), persistencia de depósitos en garantía, asignación de cláusulas activas y estado inicial `DRAFT`.

---

## 🚀 Endpoints Implementados

| Método | Ruta | Seguridad | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/contracts/generate` | `Bearer JWT (Cliente titular / Staff)` | Emite el contrato legal oficial a partir de una reserva confirmada, asignando consecutivo `CTR-YYYY-XXXX`, asociando cláusulas vigentes y fijando estado `DRAFT` |
| `GET` | `/api/v1/contracts/clauses` | `Público / PermitAll` | Consulta el catálogo de cláusulas legales vigentes ordenadas por orden secuencial (`sort_order`) |
| `GET` | `/api/v1/contracts/{id}` | `Bearer JWT (Cliente titular / Staff)` | Obtiene el contrato completo por su identificador UUID |
| `GET` | `/api/v1/contracts/reservation/{reservationId}` | `Bearer JWT (Cliente titular / Staff)` | Obtiene el contrato legal asociado a una reserva |
| `GET` | `/api/v1/contracts/number/{contractNumber}` | `Bearer JWT (Cliente titular / Staff)` | Consulta el contrato mediante su número consecutivo (ej. `CTR-2026-0001`) |

---

## 🔒 Reglas de Negocio e Integridad de Datos

1. **Unicidad y Formato del Consecutivo:**
   - Cada contrato emitido genera un número único formal bajo el formato `CTR-YYYY-XXXX` basado en el año actual en UTC y un correlativo secuencial garantizado.
2. **Asociación de Cláusulas:**
   - Se vinculan automáticamente todas las cláusulas vigentes (`is_active = true`) en la tabla intermedia `contract.contract_clause_assignments`.
3. **Monto Base y Depósito de Garantía:**
   - `base_amount` se toma directamente del total estimado de la reserva (`total_estimated`).
   - `security_deposit` se calcula a partir del depósito fijado para la categoría del vehículo reservado (`vehicle.category.security_deposit`).
4. **Validación Antiduplicidad:**
   - La tabla `rental_contracts` restringe la columna `reservation_id` con `UNIQUE`. No es posible emitir múltiples contratos para la misma reserva.
5. **Control de Acceso:**
   - Únicamente el cliente titular o los roles de administración/agente (`ADMIN`, `SUPER_ADMIN`, `AGENT`, `EMPLOYEE`, `BRANCH_ADMIN`) pueden emitir o consultar contratos.
