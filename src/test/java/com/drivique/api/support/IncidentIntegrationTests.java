package com.drivique.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Branch;
import com.drivique.api.model.BranchUser;
import com.drivique.api.model.City;
import com.drivique.api.model.Department;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.CityRepository;
import com.drivique.api.repository.DepartmentRepository;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.JwtService;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class IncidentIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private DepartmentRepository departments;
    @Autowired private CityRepository cities;
    @Autowired private BranchRepository branches;
    @Autowired private BranchUserRepository branchUsers;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder encoder;

    private Branch branchA;
    private String customerToken;
    private String otherCustomerToken;
    private String employeeAToken;
    private String employeeBToken;

    @BeforeEach
    void setUp() {
        resetIamTables();
        resetLocationTables();

        Department department = departments.saveAndFlush(new Department("Antioquia"));
        City city = cities.saveAndFlush(new City(department, "Medellín", true, true));
        branchA = branches.saveAndFlush(new Branch("Sede A", "Calle 1", city, "3001", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        Branch branchB = branches.saveAndFlush(new Branch("Sede B", "Calle 2", city, "3002", LocalTime.of(8, 0), LocalTime.of(18, 0), true));

        Role customerRole = roles.save(new Role("CUSTOMER", "Customer", "Customer", true));
        Role employeeRole = roles.save(new Role("EMPLOYEE", "Employee", "Employee", true));

        User customer = user("Cliente", "Uno", "cliente1@drivique.com", customerRole);
        User other = user("Cliente", "Dos", "cliente2@drivique.com", customerRole);
        User employeeA = user("Operador", "A", "operador.a@drivique.com", employeeRole);
        User employeeB = user("Operador", "B", "operador.b@drivique.com", employeeRole);
        branchUsers.saveAndFlush(new BranchUser(employeeA, branchA));
        branchUsers.saveAndFlush(new BranchUser(employeeB, branchB));

        customerToken = token(customer, "CUSTOMER");
        otherCustomerToken = token(other, "CUSTOMER");
        employeeAToken = token(employeeA, "EMPLOYEE");
        employeeBToken = token(employeeB, "EMPLOYEE");
    }

    private User user(String first, String last, String email, Role role) {
        User u = new User(first, last, email, encoder.encode("Pass1234!"));
        u.setRoles(Set.of(role));
        return users.save(u);
    }

    private String token(User u, String role) {
        return jwt.generateAccessToken(u.getId(), u.getEmail(), u.getFullName(), List.of(role));
    }

    private String createIncident(String subject, String description) throws Exception {
        String body = """
                {"category":"OTHER","branchId":"%s","subject":"%s","description":"%s"}
                """.formatted(branchA.getId(), subject, description);
        String response = mvc.perform(post("/api/v1/incidents").contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    @Test
    void priorityIsCalculatedByTheServerAndIgnoresClientInput() throws Exception {
        String body = """
                {"category":"OTHER","branchId":"%s","subject":"Choque en la vía","description":"Tuve un choque y necesito ayuda","priority":"LOW"}
                """.formatted(branchA.getId());
        mvc.perform(post("/api/v1/incidents").contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.branchName").value("Sede A"))
                .andExpect(jsonPath("$.slaDueAt").isNotEmpty())
                .andExpect(jsonPath("$.timeline[0].type").value("STATUS"));
    }

    @Test
    void onlyTheOwnerAndStaffOfTheBranchCanSeeAnIncident() throws Exception {
        String id = createIncident("Consulta general", "Necesito información");

        mvc.perform(get("/api/v1/incidents/" + id).contextPath("/api").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/incidents/" + id).contextPath("/api").header("Authorization", "Bearer " + employeeAToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/incidents/" + id).contextPath("/api").header("Authorization", "Bearer " + employeeBToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/incidents/" + id).contextPath("/api").header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/incidents").contextPath("/api").header("Authorization", "Bearer " + employeeAToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/incidents").contextPath("/api").header("Authorization", "Bearer " + employeeBToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void statusFollowsAllowedTransitionsAndAssignsTheOperator() throws Exception {
        String id = createIncident("Consulta general", "Necesito información");

        mvc.perform(patch("/api/v1/incidents/" + id + "/status").contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/v1/incidents/" + id + "/status").contextPath("/api")
                        .header("Authorization", "Bearer " + employeeAToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.assignedToName").value("Operador A"));

        mvc.perform(patch("/api/v1/incidents/" + id + "/status").contextPath("/api")
                        .header("Authorization", "Bearer " + employeeAToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"note\":\"Solucionado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mvc.perform(patch("/api/v1/incidents/" + id + "/status").contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.allowedTransitions.length()").value(0));
    }

    @Test
    void responsesAndPrivateAttachmentsRespectAccessAndFileRules() throws Exception {
        String id = createIncident("Consulta general", "Necesito información");

        MockMultipartFile photo = new MockMultipartFile("files", "foto.png", "image/png", new byte[] {1, 2, 3, 4});
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/incidents/" + id + "/attachments").file(photo).contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(1));

        MockMultipartFile bad = new MockMultipartFile("files", "virus.exe", "application/x-msdownload", new byte[] {1});
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/incidents/" + id + "/attachments").file(bad).contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest());

        MockMultipartFile message = new MockMultipartFile("message", "", "text/plain", "Ya lo estamos revisando".getBytes());
        mvc.perform(multipart(HttpMethod.POST, "/api/v1/incidents/" + id + "/responses").file(message).contextPath("/api")
                        .header("Authorization", "Bearer " + employeeAToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeline[?(@.type=='MESSAGE')].authorRole").value(org.hamcrest.Matchers.hasItem("STAFF")));

        String detail = mvc.perform(get("/api/v1/incidents/" + id).contextPath("/api").header("Authorization", "Bearer " + customerToken))
                .andReturn().getResponse().getContentAsString();
        String attachmentId = com.jayway.jsonpath.JsonPath.read(detail, "$.attachments[0].id");
        assertThat(attachmentId).isNotBlank();

        mvc.perform(get("/api/v1/incidents/" + id + "/attachments/" + attachmentId).contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/incidents/" + id + "/attachments/" + attachmentId).contextPath("/api")
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isNotFound());
    }
}
