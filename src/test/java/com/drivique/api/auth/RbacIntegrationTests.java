package com.drivique.api.auth;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Permission;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.PermissionRepository;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class RbacIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User superAdminUser;
    private User customerUser;
    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        resetIamTables();

        Permission readPerm = permissionRepository.save(new Permission(
                "roles:read",
                "Read Roles",
                "Read permissions and roles"
        ));

        Role adminRole = new Role(
                "SUPER_ADMIN",
                "Super Admin",
                "Super Administrator",
                true
        );
        adminRole.setPermissions(Set.of(readPerm));
        adminRole = roleRepository.save(adminRole);

        Role customerRole = roleRepository.save(new Role(
                "CUSTOMER",
                "Customer",
                "Customer",
                true
        ));

        Role employeeRole = roleRepository.save(new Role(
                "EMPLOYEE",
                "Employee",
                "Employee",
                true
        ));

        superAdminUser = new User("Super", "Admin", "superadmin@drivique.com", passwordEncoder.encode("AdminPass123!"));
        superAdminUser.setRoles(Set.of(adminRole));
        superAdminUser = userRepository.save(superAdminUser);

        customerUser = new User("Regular", "Customer", "customer@drivique.com", passwordEncoder.encode("CustPass123!"));
        customerUser.setRoles(Set.of(customerRole));
        customerUser = userRepository.save(customerUser);

        adminToken = jwtService.generateAccessToken(
                superAdminUser.getId(),
                superAdminUser.getEmail(),
                superAdminUser.getFullName(),
                List.of("SUPER_ADMIN")
        );

        customerToken = jwtService.generateAccessToken(
                customerUser.getId(),
                customerUser.getEmail(),
                customerUser.getFullName(),
                List.of("CUSTOMER")
        );
    }

    @Test
    void getRolesWithoutAuthReturns401() throws Exception {
        mvc.perform(get("/api/v1/roles").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRolesWithCustomerRoleReturns403Forbidden() throws Exception {
        mvc.perform(get("/api/v1/roles")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRolesWithSuperAdminReturns200AndRoleList() throws Exception {
        mvc.perform(get("/api/v1/roles")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").isNotEmpty());
    }

    @Test
    void getPermissionsWithSuperAdminReturns200AndList() throws Exception {
        mvc.perform(get("/api/v1/permissions")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").value("roles:read"));
    }

    @Test
    void assignRolesWithCustomerRoleReturns403Forbidden() throws Exception {
        String assignBody = """
                {
                    "roleCodes": ["EMPLOYEE"]
                }
                """;

        mvc.perform(post("/api/v1/users/" + customerUser.getId() + "/roles")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignRolesWithSuperAdminReturns200AndUpdatesUserRoles() throws Exception {
        String assignBody = """
                {
                    "roleCodes": ["CUSTOMER", "EMPLOYEE"]
                }
                """;

        mvc.perform(post("/api/v1/users/" + customerUser.getId() + "/roles")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(customerUser.getId().toString()))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItems("CUSTOMER", "EMPLOYEE")));

        User updatedUser = userRepository.findById(customerUser.getId()).orElseThrow();
        assertThat(updatedUser.getRoles()).hasSize(2);
    }
}
