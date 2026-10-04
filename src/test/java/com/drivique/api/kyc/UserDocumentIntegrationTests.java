package com.drivique.api.kyc;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.JwtService;
import com.drivique.api.model.DocumentStatus;
import com.drivique.api.model.DocumentType;
import com.drivique.api.model.UserDocument;
import com.drivique.api.repository.DocumentStatusRepository;
import com.drivique.api.repository.DocumentTypeRepository;
import com.drivique.api.repository.UserDocumentRepository;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class UserDocumentIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private DocumentStatusRepository documentStatusRepository;

    @Autowired
    private UserDocumentRepository userDocumentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customerUser;
    private User employeeUser;
    private String customerToken;
    private String employeeToken;
    private DocumentType ccDocType;
    private DocumentStatus pendingStatus;
    @SuppressWarnings("unused")
    private DocumentStatus approvedStatus;

    @BeforeEach
    void setUp() {
        resetIamTables();

        pendingStatus = documentStatusRepository.save(new DocumentStatus("PENDING", "Pendiente", "Pendiente de revisión"));
        approvedStatus = documentStatusRepository.save(new DocumentStatus("APPROVED", "Aprobado", "Aprobado"));
        documentStatusRepository.save(new DocumentStatus("REJECTED", "Rechazado", "Rechazado"));

        ccDocType = documentTypeRepository.save(new DocumentType(
                "CC",
                "Cédula de Ciudadanía",
                "Documento de identidad",
                true,
                true,
                true
        ));

        Role customerRole = roleRepository.save(new Role("CUSTOMER", "Customer", "Customer", true));
        Role employeeRole = roleRepository.save(new Role("EMPLOYEE", "Employee", "Employee", true));

        customerUser = new User("Customer", "User", "customer.kyc@drivique.com", passwordEncoder.encode("Pass1234!"));
        customerUser.setRoles(Set.of(customerRole));
        customerUser = userRepository.save(customerUser);

        employeeUser = new User("Auditor", "Employee", "employee.kyc@drivique.com", passwordEncoder.encode("Pass1234!"));
        employeeUser.setRoles(Set.of(employeeRole));
        employeeUser = userRepository.save(employeeUser);

        customerToken = jwtService.generateAccessToken(
                customerUser.getId(),
                customerUser.getEmail(),
                customerUser.getFullName(),
                List.of("CUSTOMER")
        );

        employeeToken = jwtService.generateAccessToken(
                employeeUser.getId(),
                employeeUser.getEmail(),
                employeeUser.getFullName(),
                List.of("EMPLOYEE")
        );
    }

    @Test
    void getDocumentTypesReturns200AndActiveList() throws Exception {
        mvc.perform(get("/api/v1/kyc/document-types").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").value("CC"))
                .andExpect(jsonPath("$[0].requiresFrontAndBack").value(true));
    }

    @Test
    void uploadDocumentWithoutAuthReturns401() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontFile", "front.jpg", "image/jpeg", "front-data".getBytes());

        mvc.perform(multipart(HttpMethod.POST, "/api/v1/users/me/documents")
                        .file(front)
                        .param("documentTypeId", ccDocType.getId().toString())
                        .contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadDocumentWithCustomerTokenSucceedsAndCreatesPendingDoc() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontFile", "front.jpg", "image/jpeg", "front-data".getBytes());
        MockMultipartFile back = new MockMultipartFile("backFile", "back.jpg", "image/jpeg", "back-data".getBytes());

        mvc.perform(multipart(HttpMethod.POST, "/api/v1/users/me/documents")
                        .file(front)
                        .file(back)
                        .param("documentTypeId", ccDocType.getId().toString())
                        .param("documentNumber", "123456789")
                        .header("Authorization", "Bearer " + customerToken)
                        .contextPath("/api"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value("PENDING"))
                .andExpect(jsonPath("$.documentNumber").value("123456789"))
                .andExpect(jsonPath("$.frontUrl").value(org.hamcrest.Matchers.startsWith("/uploads/kyc/")))
                .andExpect(jsonPath("$.backUrl").value(org.hamcrest.Matchers.startsWith("/uploads/kyc/")));

        List<UserDocument> docs = userDocumentRepository.findByUserId(customerUser.getId());
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getStatus().getCode()).isEqualTo("PENDING");
    }

    @Test
    void uploadDocumentWithInvalidFormatReturns400BadRequest() throws Exception {
        MockMultipartFile badFile = new MockMultipartFile("frontFile", "doc.exe", "application/x-msdownload", "executable".getBytes());

        mvc.perform(multipart(HttpMethod.POST, "/api/v1/users/me/documents")
                        .file(badFile)
                        .param("documentTypeId", ccDocType.getId().toString())
                        .header("Authorization", "Bearer " + customerToken)
                        .contextPath("/api"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void reviewDocumentWithCustomerTokenReturns403Forbidden() throws Exception {
        UserDocument doc = userDocumentRepository.save(new UserDocument(
                customerUser,
                ccDocType,
                pendingStatus,
                "123456789",
                "/uploads/kyc/front.jpg",
                "/uploads/kyc/back.jpg"
        ));

        String reviewBody = """
                {
                    "status": "APPROVED",
                    "reviewNotes": "Documento verificado correctamente."
                }
                """;

        mvc.perform(patch("/api/v1/kyc/documents/" + doc.getId() + "/review")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewDocumentWithEmployeeTokenApprovesAndSetsProfileComplete() throws Exception {
        UserDocument doc = userDocumentRepository.save(new UserDocument(
                customerUser,
                ccDocType,
                pendingStatus,
                "123456789",
                "/uploads/kyc/front.jpg",
                "/uploads/kyc/back.jpg"
        ));

        String reviewBody = """
                {
                    "status": "APPROVED",
                    "reviewNotes": "Documento verificado correctamente y válido."
                }
                """;

        mvc.perform(patch("/api/v1/kyc/documents/" + doc.getId() + "/review")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("APPROVED"))
                .andExpect(jsonPath("$.reviewNotes").value("Documento verificado correctamente y válido."))
                .andExpect(jsonPath("$.reviewedById").value(employeeUser.getId().toString()));

        User updatedCustomer = userRepository.findById(customerUser.getId()).orElseThrow();
        assertThat(updatedCustomer.isProfileComplete()).isTrue();
    }
}
