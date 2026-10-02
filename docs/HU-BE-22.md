# HU-BE-22 — Servicios adicionales y coberturas

Consume `catalog.additional_services` y `catalog.insurance_coverages` de HU-BD-23.
Los listados públicos usan caché de cinco minutos. Los `POST` y `PUT` administrativos
están protegidos para `SUPER_ADMIN` y actualizan las tarifas del cotizador.
