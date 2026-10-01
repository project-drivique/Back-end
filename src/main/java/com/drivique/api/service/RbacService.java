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
