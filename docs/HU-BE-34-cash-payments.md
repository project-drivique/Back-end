# HU-BE-34 — Confirmación de pagos en efectivo

`POST /api/v1/payments/cash/confirm` requiere rol `EMPLOYEE` o `BRANCH_ADMIN`. Valida el código de la reserva, la vigencia y que su sede acepte efectivo; registra un pago `EFECTIVO_CAJA` aprobado con el cajero confirmado y cambia la reserva a `CONFIRMED`.
