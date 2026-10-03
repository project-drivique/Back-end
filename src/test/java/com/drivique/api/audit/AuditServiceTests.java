package com.drivique.api.audit;

import com.drivique.api.dto.AuditLogResponseDTO;
import com.drivique.api.dto.RecordAuditLogRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.AuditLog;
import com.drivique.api.model.Branch;
import com.drivique.api.model.User;
import com.drivique.api.repository.AuditLogRepository;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.AuditService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BranchRepository branchRepository;

    private ObjectMapper objectMapper;
    private AuditService auditService;

    private User sampleUser;
    private Branch sampleBranch;

    @BeforeEach
    void setUp() {
        objectMapper = new JsonMapper();
        auditService = new AuditService(auditLogRepository, userRepository, branchRepository, objectMapper);

        sampleUser = new User("Audit", "Admin", "audit.admin@drivique.com", "pass123");
        sampleUser.setId(UUID.randomUUID());

        sampleBranch = org.mockito.Mockito.mock(Branch.class);
    }

    @Test
    void getAuditLogs_WithFilters_ReturnsList() {
        AuditLog log1 = new AuditLog(
                "FLEET", "Vehicle", UUID.randomUUID(), "CREATE", "SUCCESS",
                sampleUser, sampleBranch, "192.168.1.100", "Creación de vehículo", null, "{\"plate\":\"ABC-123\"}"
        );
        log1.setId(UUID.randomUUID());

        when(auditLogRepository.findAll(any(Specification.class))).thenReturn(List.of(log1));

        List<AuditLogResponseDTO> logs = auditService.getAuditLogs(
                "Vehicle", "audit.admin@drivique.com", LocalDate.now().minusDays(7), LocalDate.now(), "CREATE", "FLEET"
        );

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).entityName()).isEqualTo("Vehicle");
        assertThat(logs.get(0).domainName()).isEqualTo("FLEET");
        assertThat(logs.get(0).actorEmail()).isEqualTo("audit.admin@drivique.com");
    }

    @Test
    void getAuditLogById_WhenFound_ReturnsDTO() {
        UUID id = UUID.randomUUID();
        AuditLog log = new AuditLog(
                "BILLING", "Payment", UUID.randomUUID(), "CHARGE", "SUCCESS",
                sampleUser, sampleBranch, "10.0.0.1", "Cobro procesado", "{}", "{\"status\":\"PAID\"}"
        );
        log.setId(id);

        when(auditLogRepository.findById(id)).thenReturn(Optional.of(log));

        AuditLogResponseDTO result = auditService.getAuditLogById(id);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.domainName()).isEqualTo("BILLING");
    }

    @Test
    void getAuditLogById_WhenNotFound_ThrowsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(auditLogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auditService.getAuditLogById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Registro de auditoría no encontrado");
    }

    @Test
    void recordAudit_WithEntitiesAndObjects_SerializesJsonSuccessfully() {
        UUID entityId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(sampleBranch.getId()).thenReturn(branchId);
        when(sampleBranch.getName()).thenReturn("Sede El Poblado");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("audit.admin@drivique.com"))
                .thenReturn(Optional.of(sampleUser));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(sampleBranch));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Map<String, Object> oldState = Map.of("status", "PENDING", "mileage", 15000);
        Map<String, Object> newState = Map.of("status", "AVAILABLE", "mileage", 15000);

        AuditLogResponseDTO response = auditService.recordAudit(
                "FLEET",
                "Vehicle",
                entityId,
                "UPDATE",
                "SUCCESS",
                "audit.admin@drivique.com",
                branchId,
                "200.1.2.3",
                "Actualización de vehículo",
                oldState,
                newState
        );

        assertThat(response).isNotNull();
        assertThat(response.domainName()).isEqualTo("FLEET");
        assertThat(response.entityName()).isEqualTo("Vehicle");
        assertThat(response.entityId()).isEqualTo(entityId);
        assertThat(response.actorEmail()).isEqualTo("audit.admin@drivique.com");
        assertThat(response.branchName()).isEqualTo("Sede El Poblado");
        assertThat(response.oldData()).contains("\"status\":\"PENDING\"");
        assertThat(response.newData()).contains("\"status\":\"AVAILABLE\"");

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void recordLog_ViaDTO_SavesAuditLog() {
        UUID entityId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(sampleBranch.getId()).thenReturn(branchId);
        when(userRepository.findById(sampleUser.getId())).thenReturn(Optional.of(sampleUser));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(sampleBranch));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        RecordAuditLogRequestDTO req = new RecordAuditLogRequestDTO(
                "IAM",
                "User",
                entityId,
                "ROLE_ASSIGNMENT",
                "SUCCESS",
                sampleUser.getId(),
                branchId,
                "127.0.0.1",
                "Asignación de rol ADMIN",
                "{\"roles\":[\"CUSTOMER\"]}",
                "{\"roles\":[\"CUSTOMER\",\"ADMIN\"]}"
        );

        AuditLogResponseDTO response = auditService.recordLog(req);

        assertThat(response).isNotNull();
        assertThat(response.domainName()).isEqualTo("IAM");
        assertThat(response.entityName()).isEqualTo("User");
        assertThat(response.operation()).isEqualTo("ROLE_ASSIGNMENT");
        assertThat(response.actorEmail()).isEqualTo("audit.admin@drivique.com");
    }
}
