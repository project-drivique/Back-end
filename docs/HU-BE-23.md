# HU-BE-23 — Planes de kilometraje

Consume `catalog.mileage_plans` de HU-BD-24. Expone planes activos al público y
permite a `SUPER_ADMIN` crearlos o actualizar sus kilómetros incluidos, tarifa diaria
y recargo por kilómetro adicional. El listado usa caché de cinco minutos.
