# HU-BE-33 — Wompi Checkout, webhooks y tokenización

`POST /api/v1/payments/wompi/initiate` crea un pago pendiente y entrega los valores necesarios para el Widget/Web Checkout: llave pública, referencia única, monto COP en centavos y firma SHA-256 de integridad.

`POST /api/v1/payments/wompi/webhook` no exige JWT: verifica `signature.properties`, `signature.checksum` o el encabezado `X-Event-Checksum` con el secreto de eventos antes de aceptar un cambio a `APPROVED` o `DECLINED`. Un pago aprobado confirma su reserva.

`POST /api/v1/payments/wompi/payment-methods` persiste exclusivamente el token emitido por Wompi, marca, últimos cuatro y vencimiento. Rechaza valores que parezcan PAN, por lo que nunca almacena datos sensibles de tarjeta.

Configurar por entorno: `WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET` y `WOMPI_EVENT_SECRET`. Las tablas son las existentes en el repositorio `database`: `billing.payments`, `billing.payment_methods`, `billing.payment_statuses` y `billing.user_saved_payment_methods`.
