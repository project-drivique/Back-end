package com.drivique.api.service;

import com.drivique.api.dto.AssignRolesRequestDTO;
import com.drivique.api.dto.PermissionResponseDTO;
import com.drivique.api.dto.RoleResponseDTO;
import com.drivique.api.dto.UserRolesResponseDTO;
import com.drivique.api.model.Permission;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.PermissionRepository;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RbacService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public RbacService(RoleRepository roleRepository, PermissionRepository permissionRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponseDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapRoleToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponseDTO> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapPermissionToDTO)
                .toList();
    }

    @Transactional
    public UserRolesResponseDTO assignRolesToUser(UUID userId, AssignRolesRequestDTO request) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        Set<Role> roles = new HashSet<>();
        for (String code : request.roleCodes()) {
            Role role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado con código: " + code));
            roles.add(role);
        }

        targetUser.setRoles(roles);
        targetUser.setUpdatedAt(Instant.now());
        User saved = userRepository.save(targetUser);

        List<String> roleCodes = saved.getRoles().stream().map(Role::getCode).toList();
        return new UserRolesResponseDTO(saved.getId(), saved.getEmail(), roleCodes);
    }

    private static final Set<String> SYSTEM_ROLES = Set.of("SUPER_ADMIN", "BRANCH_ADMIN", "EMPLOYEE", "CUSTOMER");

    @Transactional
    @com.drivique.api.config.Auditable(domain = "IAM", entity = "Role", action = "INSERT", description = "Creación de rol")
    public RoleResponseDTO createRole(com.drivique.api.dto.RoleRequestDTO request) {
        String name = request.name().strip();
        String code = name.toUpperCase(java.util.Locale.ROOT)
                .replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U').replace('Ñ', 'N')
                .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.length() < 3 || !Character.isLetter(code.charAt(0))) {
            throw new IllegalArgumentException("El nombre del rol debe tener al menos 3 caracteres y empezar con una letra");
        }
        if (code.length() > 40) {
            code = code.substring(0, 40);
        }
        if (roleRepository.findByCode(code).isPresent() || roleRepository.findAll().stream().anyMatch(r -> r.getName().equalsIgnoreCase(name))) {
            throw new com.drivique.api.exception.ConflictException("Ya existe un rol con ese nombre");
        }
        Role role = new Role(code, name, request.description().strip(), true);
        role.setPermissions(resolvePermissions(request.permissionCodes()));
        return mapRoleToDTO(roleRepository.saveAndFlush(role));
    }

    @Transactional
    @com.drivique.api.config.Auditable(domain = "IAM", entity = "Role", action = "UPDATE", description = "Actualización de rol y permisos")
    public RoleResponseDTO updateRole(UUID id, com.drivique.api.dto.RoleRequestDTO request) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new com.drivique.api.exception.ConflictException("El rol de super administrador no se puede modificar");
        }
        String name = request.name().strip();
        if (roleRepository.findAll().stream().anyMatch(r -> !r.getId().equals(id) && r.getName().equalsIgnoreCase(name))) {
            throw new com.drivique.api.exception.ConflictException("Ya existe un rol con ese nombre");
        }
        // El código no cambia nunca: los roles del sistema se identifican por él
        role.update(SYSTEM_ROLES.contains(role.getCode()) ? role.getName() : name, request.description().strip());
        role.setPermissions(resolvePermissions(request.permissionCodes()));
        return mapRoleToDTO(roleRepository.saveAndFlush(role));
    }

    @Transactional
    @com.drivique.api.config.Auditable(domain = "IAM", entity = "Role", action = "DELETE", description = "Eliminación de rol")
    public void deleteRole(UUID id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));
        if (SYSTEM_ROLES.contains(role.getCode())) {
            throw new com.drivique.api.exception.ConflictException("Los roles del sistema no se pueden eliminar");
        }
        if (userRepository.findAll().stream().anyMatch(u -> u.getDeletedAt() == null
                && u.getRoles().stream().anyMatch(r -> r.getId().equals(id)))) {
            throw new com.drivique.api.exception.ConflictException("El rol tiene cuentas asignadas; reasígnalas antes de eliminarlo");
        }
        roleRepository.delete(role);
        roleRepository.flush();
    }

    private Set<Permission> resolvePermissions(List<String> codes) {
        Set<Permission> resolved = new HashSet<>();
        if (codes != null) {
            for (String code : codes) {
                resolved.add(permissionRepository.findByCode(code)
                        .orElseThrow(() -> new ResourceNotFoundException("Permiso no encontrado: " + code)));
            }
        }
        return resolved;
    }

    private RoleResponseDTO mapRoleToDTO(Role role) {
        List<PermissionResponseDTO> permissions = role.getPermissions().stream()
                .map(this::mapPermissionToDTO)
                .toList();

        return new RoleResponseDTO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isActive(),
                permissions
        );
    }

    private PermissionResponseDTO mapPermissionToDTO(Permission permission) {
        return new PermissionResponseDTO(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription()
        );
    }
}
