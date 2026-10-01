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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacServiceTests {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    private RbacService rbacService;
    private User testUser;
    private Role customerRole;
    private Role employeeRole;
    private Permission readPermission;

    @BeforeEach
    void setUp() {
        rbacService = new RbacService(roleRepository, permissionRepository, userRepository);

        readPermission = new Permission(UUID.randomUUID(), "roles:read", "Read Roles", "Permission to read roles");
        customerRole = new Role(UUID.randomUUID(), "CUSTOMER", "Customer", "Customer role", true);
        employeeRole = new Role(UUID.randomUUID(), "EMPLOYEE", "Employee", "Employee role", true);
        employeeRole.setPermissions(Set.of(readPermission));

        testUser = new User("Carlos", "Gomez", "carlos@drivique.com", "$2a$12$hash");
        testUser.setId(UUID.randomUUID());
        testUser.setRoles(Set.of(customerRole));
    }

    @Test
    void getAllRolesReturnsRolesWithPermissions() {
        when(roleRepository.findAll()).thenReturn(List.of(customerRole, employeeRole));

        List<RoleResponseDTO> roles = rbacService.getAllRoles();

        assertThat(roles).hasSize(2);
        assertThat(roles.get(1).permissions()).hasSize(1);
        assertThat(roles.get(1).permissions().getFirst().code()).isEqualTo("roles:read");
    }

    @Test
    void getAllPermissionsReturnsList() {
        when(permissionRepository.findAll()).thenReturn(List.of(readPermission));

        List<PermissionResponseDTO> permissions = rbacService.getAllPermissions();

        assertThat(permissions).hasSize(1);
        assertThat(permissions.getFirst().code()).isEqualTo("roles:read");
    }

    @Test
    void assignRolesSuccessUpdatesUserRoles() {
        AssignRolesRequestDTO request = new AssignRolesRequestDTO(List.of("CUSTOMER", "EMPLOYEE"));

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(roleRepository.findByCode("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserRolesResponseDTO response = rbacService.assignRolesToUser(testUser.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(testUser.getId());
        assertThat(response.roles()).containsExactlyInAnyOrder("CUSTOMER", "EMPLOYEE");
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void assignRolesUserNotFoundThrowsResourceNotFoundException() {
        UUID randomId = UUID.randomUUID();
        AssignRolesRequestDTO request = new AssignRolesRequestDTO(List.of("CUSTOMER"));

        when(userRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rbacService.assignRolesToUser(randomId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignRolesRoleNotFoundThrowsResourceNotFoundException() {
        AssignRolesRequestDTO request = new AssignRolesRequestDTO(List.of("NON_EXISTENT_ROLE"));

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(roleRepository.findByCode("NON_EXISTENT_ROLE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rbacService.assignRolesToUser(testUser.getId(), request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
