# HU-BE-32 — Inspecciones físicas de vehículos

`POST /api/v1/contracts/{contractId}/inspections` registra `CHECK_IN` y `CHECK_OUT` mediante `multipart/form-data` para usuarios con permiso `fleet:manage` o rol administrativo.

La parte `request` es JSON con `inspectionType`, `mileage`, `fuelLevelPercent`, `observations` y `answers`. Cada respuesta incluye `checklistItemId`, `compliant`, `observation` y, cuando corresponde, `evidencePhotoIndex`. La parte opcional `evidencePhotos` contiene las imágenes JPG/PNG referidas por ese índice.

Los ítems no conformes exigen observación y evidencia. Solo se permite una inspección de cada tipo por contrato; el `CHECK_OUT` requiere un `CHECK_IN`, calcula la diferencia de kilometraje y combustible, calcula el cobro por kilometraje adicional según el plan contratado y actualiza el kilometraje final del vehículo.

Las entidades siguen las tablas existentes del repositorio `database`: `contract.vehicle_inspections`, `contract.inspection_checklist_answers` y `contract.inspection_checklist_items`. El esquema no se replica ni se modifica en este repositorio.
