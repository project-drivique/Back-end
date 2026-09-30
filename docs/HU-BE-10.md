# HU-BE-10 — Documentación KYC: Subida y Verificación de Documentos de Identidad

## Resumen de Implementación

Se ha implementado el módulo completo de documentación y verificación pericial de identidad (KYC) para Drivique, cumpliendo estrictamente con todos los requerimientos de **HU-BE-10**:

1. **Modelado y Persistencia JPA:**
   - `DocumentType` (`iam.document_types`): Catálogo de tipos de documento oficiales (CC, CE, PASSPORT, TI, DRIVER_LICENSE) con indicadores `requires_front_and_back`, `is_mandatory` e `is_active`.
   - `DocumentStatus` (`iam.document_statuses`): Catálogo de estados de verificación (`PENDING`, `APPROVED`, `REJECTED`).
   - `UserDocument` (`iam.user_documents`): Entidad principal de documentos cargados por el usuario con vínculos a usuario, tipo de documento, estado, URLs frontal y trasera, notas de revisión pericial, auditor (`reviewed_by`) y fecha de dictamen (`reviewed_at`).
   - `UserDocumentRepository`, `DocumentTypeRepository`, `DocumentStatusRepository`: Repositorios Spring Data JPA para consultas optimizadas y control de trazabilidad.

2. **Servicio de Almacenamiento Seguro de Archivos (`FileStorageService` & `LocalFileStorageService`):**
   - Validación estricta de tipos MIME permitidos (`image/jpeg`, `image/jpg`, `image/png`, `application/pdf`) y extensiones (`.jpg`, `.jpeg`, `.png`, `.pdf`).
   - Control de tamaño de archivo (máximo 5MB por archivo).
   - Generación de nombres de archivo seguros e inmunes a ataques de Path Traversal mediante UUID.
   - Directorio de almacenamiento configurable mediante la propiedad `app.upload.dir`.

3. **Lógica de Negocio y Ciclo de Vida KYC (`UserDocumentService`):**
   - `uploadDocument`: Permite al cliente subir su documento (frontal y reverso si el tipo lo exige). Si ya existe un registro previo para ese tipo, lo actualiza y reinicia el estado a `PENDING`.
   - `getUserDocuments`: Consulta el estado actual de los documentos del usuario autenticado.
   - `reviewDocument`: Auditoría pericial por parte de agentes o administradores (`EMPLOYEE`, `BRANCH_ADMIN`, `SUPER_ADMIN` o permiso `kyc:review`). Actualiza el estado (`APPROVED` o `REJECTED`), notas de auditoría, auditor y fecha.
   - **Completitud de Perfil Automática:** Valida si todos los tipos de documento obligatorios (`is_mandatory = true`) del usuario se encuentran en estado `APPROVED`. Al cumplirse, actualiza automáticamente `user.is_profile_complete = true` en la tabla `iam.users`.

4. **Capa REST y Documentación OpenAPI (`UserDocumentController` & `KycAuditController`):**
   - `GET /v1/kyc/document-types`: Catálogo público de tipos de documento soportados.
   - `POST /v1/users/me/documents`: Subida multipart de documento de identidad/licencia por el usuario autenticado (retorna 201 Created).
   - `GET /v1/users/me/documents`: Consulta de documentos y estados del usuario autenticado.
   - `PATCH /v1/kyc/documents/{id}/review`: Dictamen pericial protegido con roles autorizados.
   - `GET /v1/kyc/documents`: Listado de documentos para revisión de agentes.
   - Documentación exhaustiva en OpenAPI 3.0 / Swagger UI.

5. **Pruebas Automatizadas:**
   - `FileStorageServiceTests`: Pruebas unitarias de almacenamiento, validación de tipos MIME y límite de tamaño 5MB.
   - `UserDocumentServiceTests`: Pruebas unitarias de subida, requerimiento de reverso y aprobación con actualización de `is_profile_complete`.
   - `UserDocumentIntegrationTests`: Pruebas de integración E2E con MockMvc y Testcontainers PostgreSQL verificando flujos de subida, control de acceso por roles (401, 403) y dictamen pericial.
