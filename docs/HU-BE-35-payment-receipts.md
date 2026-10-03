# HU-BE-35 — Comprobantes de pago

Los pagos aprobados generan un consecutivo `RCP-YYYY-######`, almacenan el PDF en `billing.payment_receipts` y usan `payments/receipts` como ubicación de archivo. `GET /api/v1/payments/{id}/receipt` descarga el comprobante para su cliente o personal autorizado; incluye subtotal, IVA del 19 %, total y código QR verificable.
