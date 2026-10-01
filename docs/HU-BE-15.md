# HU-BE-15 — Catálogos técnicos de flota

Consume las tablas de HU-BD-17 del esquema `fleet`: marcas, transmisiones,
combustibles y estados. Los cuatro `GET` son públicos y devuelven solo registros
activos; los estados se filtran además por `allows_reservation = true`.

`POST` y `PUT /api/v1/vehicle-brands` requieren `SUPER_ADMIN`. Los listados se
almacenan cinco minutos en la caché de aplicación y la edición de marcas la invalida.
