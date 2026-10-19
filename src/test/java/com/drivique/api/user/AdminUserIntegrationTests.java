package com.drivique.api.user;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AdminUserIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private BranchRepository branches;
    @Autowired private CityRepository cities;
    @Autowired private DepartmentRepository departments;
    @Autowired private BranchUserRepository assignments;
    @Autowired private PasswordEncoder encoder;

    private Branch branch;
    private User customer;

    @BeforeEach
    void setup() {
        resetIamTables();
        resetLocationTables();
        roles.save(new Role("CUSTOMER", "Customer", "Customer role", true));
        roles.save(new Role("BRANCH_ADMIN", "Branch Administrator", "Branch role", true));
        Role superAdmin = roles.save(new Role("SUPER_ADMIN", "Super Administrator", "Admin role", true));
        User admin = new User("Admin", "General", "admin@drivique.com", encoder.encode("irrelevant"));
        admin.setRoles(Set.of(superAdmin));
        users.save(admin);

        Department dep = departments.saveAndFlush(new Department("Antioquia"));
        City city = cities.saveAndFlush(new City(dep, "Medellín", true, true));
        branch = branches.saveAndFlush(new Branch("Sede Poblado", "Calle 10 # 5-20", city, "3001234567",
                LocalTime.of(8, 0), LocalTime.of(18, 0), true));

        customer = new User("Carlos", "Mendoza", "carlos@correo.com", encoder.encode("irrelevant"));
        customer.setRoles(Set.of(roles.findByCode("CUSTOMER").orElseThrow()));
        customer = users.save(customer);
    }

    private String body(String name, String email, String role, String branchId) {
        return """
                {"fullName": "%s", "email": "%s", "phone": "3001112233", "documentNumber": "1020304050",
                 "roleCode": "%s", "branchId": %s}
                """.formatted(name, email, role, branchId == null ? "null" : "\"" + branchId + "\"");
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void adminCreatesBranchManagerAndListsUsersWithRoleAndBranch() throws Exception {
        mvc.perform(post("/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Laura Encargada", "laura@drivique.com", "BRANCH_ADMIN", branch.getId().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleCode").value("BRANCH_ADMIN"))
                .andExpect(jsonPath("$.branchName").value("Sede Poblado"))
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(get("/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        assertThat(users.findByEmailIgnoreCaseAndDeletedAtIsNull("laura@drivique.com")).isPresent();
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void branchManagerWithoutBranchIsAcceptedAndDuplicatedEmailIsRejected() throws Exception {
        // Desde e540cc8 el servicio ya no exige sucursal al crear un encargado (se puede asignar después).
        mvc.perform(post("/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Laura Encargada", "laura@drivique.com", "BRANCH_ADMIN", null)))
                .andExpect(status().isCreated());
        mvc.perform(post("/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Otro Carlos", "carlos@correo.com", "CUSTOMER", null)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void adminUpdatesRoleAssignsBranchTogglesAndDeletes() throws Exception {
        mvc.perform(put("/v1/admin/users/" + customer.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Carlos Mendoza Ruiz", "carlos@correo.com", "BRANCH_ADMIN", branch.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Carlos Mendoza Ruiz"))
                .andExpect(jsonPath("$.roleCode").value("BRANCH_ADMIN"))
                .andExpect(jsonPath("$.branchName").value("Sede Poblado"));

        mvc.perform(patch("/v1/admin/users/" + customer.getId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("INACTIVE"));

        mvc.perform(delete("/v1/admin/users/" + customer.getId())).andExpect(status().isNoContent());
        assertThat(users.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@correo.com")).isEmpty();
        assertThat(assignments.findByUserId(customer.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void adminCannotDeactivateOrDeleteOwnAccount() throws Exception {
        String adminId = users.findByEmailIgnoreCaseAndDeletedAtIsNull("admin@drivique.com").orElseThrow().getId().toString();
        mvc.perform(patch("/v1/admin/users/" + adminId + "/status")).andExpect(status().isConflict());
        mvc.perform(delete("/v1/admin/users/" + adminId)).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotListUsers() throws Exception {
        mvc.perform(get("/v1/admin/users")).andExpect(status().isForbidden());
    }

    @Autowired private PermissionRepository permissionRepository;

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void adminManagesCustomRolesWithPermissionsAndAssignsThemToUsers() throws Exception {
        permissionRepository.save(new Permission("FLEET_MANAGE", "Manage Fleet", "Fleet"));
        permissionRepository.save(new Permission("REPORTS_READ", "Read Reports", "Reports"));

        String created = mvc.perform(post("/v1/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Auditor Interno\", \"description\": \"Solo lectura\", \"permissionCodes\": [\"REPORTS_READ\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("AUDITOR_INTERNO"))
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String roleId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asText();

        mvc.perform(post("/v1/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"auditor interno\", \"description\": \"x\", \"permissionCodes\": []}"))
                .andExpect(status().isConflict());

        mvc.perform(put("/v1/roles/" + roleId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Auditor Interno\", \"description\": \"Lectura y flota\", \"permissionCodes\": [\"REPORTS_READ\", \"FLEET_MANAGE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2));

        // Un rol personalizado se puede asignar a una cuenta
        mvc.perform(post("/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana Auditora", "ana@drivique.com", "AUDITOR_INTERNO", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roleCode").value("AUDITOR_INTERNO"));

        mvc.perform(delete("/v1/roles/" + roleId)).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void systemRolesCannotBeDeletedNorTheSuperAdminModified() throws Exception {
        String customerRole = roles.findByCode("CUSTOMER").orElseThrow().getId().toString();
        String superRole = roles.findByCode("SUPER_ADMIN").orElseThrow().getId().toString();
        mvc.perform(delete("/v1/roles/" + customerRole)).andExpect(status().isConflict());
        mvc.perform(put("/v1/roles/" + superRole).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Otro\", \"description\": \"x\", \"permissionCodes\": []}"))
                .andExpect(status().isConflict());
    }
}
