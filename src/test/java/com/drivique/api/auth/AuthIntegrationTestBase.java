package com.drivique.api.auth;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
abstract class AuthIntegrationTestBase extends DatabaseHealthTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected com.drivique.api.repository.VerificationCodeRepository verificationCodeRepository;

    @Autowired
    protected com.drivique.api.service.VerificationCodeService verificationCodeService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @MockitoBean
    protected com.drivique.api.service.AuthEmailService authEmailService;

    protected User testUser;

    protected Role customerRole;

    @BeforeEach
    void setUp() {
        resetIamTables();

        jdbc.execute("DELETE FROM iam.password_policies");
        jdbc.execute("INSERT INTO iam.password_policies (id, min_length, require_uppercase, require_number, require_symbol, is_active) VALUES ('" + UUID.randomUUID() + "', 8, true, true, true, true)");

        customerRole = roleRepository.save(new Role(
                "CUSTOMER",
                "Customer",
                "Customer role",
                true
        ));

        roleRepository.save(new Role(
                "SUPER_ADMIN",
                "Super Admin",
                "Super Admin role",
                true
        ));

        testUser = new User("Juan", "Perez", "juan.perez@drivique.com", passwordEncoder.encode("SecureP@ssw0rd123"));
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);
    }

    protected org.springframework.test.web.servlet.ResultActions changePassword(String current, String next) throws Exception {
        return mvc.perform(post("/api/v1/auth/change-password")
                .contextPath("/api")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("juan.perez@drivique.com").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    protected int loginStatus(String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"juan.perez@drivique.com\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }
}
