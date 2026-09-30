# HU-BE-09 — Matriz RBAC: Gestión de Roles, Permisos y Asignaciones

## Resumen de Implementación

Se ha implementado el módulo de matriz de control de acceso basada en roles (RBAC) y gestión de permisos del sistema para Drivique, cumpliendo con la historia **HU-BE-09**:

1. **Modelado y Persistencia JPA:**
   - `Permission` (`iam.permissions`): Entidad que representa los privilegios atómicos del sistema (`roles:read`, `roles:assign`, etc.).
   - `Role` (`iam.roles`): Mapeo `@ManyToMany` con `iam.permissions` mediante la tabla intermedia `iam.role_permissions`.
   - `PermissionRepository` y `RoleRepository`: Repositorios Spring Data JPA para consultar catálogos de roles y permisos.

2. **Lógica de Negocio y Servicios:**
   - `RbacService`:
     - `getAllRoles`: Consulta todos los roles activos junto con sus permisos asignados.
     - `getAllPermissions`: Consulta el catálogo de permisos del sistema.
     - `assignRolesToUser`: Asigna roles a un usuario específico y persiste los cambios actualizando `updated_at`.

3. **Capa REST y Control de Acceso (`@PreAuthorize`):**
   - `RoleController` (`/v1/roles`):
     - `GET /v1/roles`: Protegido con `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('roles:read')")`.
   - `PermissionController` (`/v1/permissions`):
     - `GET /v1/permissions`: Protegido con `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('roles:read')")`.
   - `UserController` (`/v1/users/{id}/roles`):
     - `POST /v1/users/{id}/roles`: Protegido con `@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('roles:assign')")`.

4. **Pruebas Automatizadas:**
   - `RbacServiceTests`: Pruebas unitarias de consulta de roles, permisos y asignación de roles a usuarios.
   - `RbacIntegrationTests`: Pruebas de integración MockMvc validando acceso denegado (403 Forbidden) para roles sin privilegios (`CUSTOMER`) y acceso exitoso (200 OK) para `SUPER_ADMIN`.
