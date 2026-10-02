# HU-BE-31 — Firma biométrica y PDF de contrato

`POST /api/v1/contracts/{id}/sign` recibe la firma PNG, los trazos biométricos JSON
y la ciudad de firma. Solo el cliente del contrato pendiente puede firmar. Se almacena
la firma, se genera el PDF con OpenPDF, se persiste su URL y el contrato pasa a `ACTIVE`.
