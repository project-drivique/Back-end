package com.drivique.api.service;

import com.drivique.api.dto.AuditLogResponseDTO;
import com.drivique.api.dto.RecordAuditLogRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.AuditLog;
import com.drivique.api.model.Branch;
import com.drivique.api.model.User;
import com.drivique.api.repository.AuditLogRepository;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            BranchRepository branchRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponseDTO> getAuditLogs(
            String entityName,
            String userEmail,
            LocalDate startDate,
            LocalDate endDate,
            String operation,
            String domain
    ) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (entityName != null && !entityName.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("entityName")), entityName.trim().toLowerCase()));
            }

            if (domain != null && !domain.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("domainName")), domain.trim().toUpperCase()));
            }

            if (operation != null && !operation.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("operation")), operation.trim().toUpperCase()));
            }

            if (userEmail != null && !userEmail.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("actor").get("email")), userEmail.trim().toLowerCase()));
            }

            if (startDate != null) {
                Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startInstant));
            }

            if (endDate != null) {
                Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), endInstant));
            }

            if (query != null) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec).stream()
                .map(AuditLogResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AuditLogResponseDTO getAuditLogById(UUID id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registro de auditoría no encontrado con ID: " + id));
        return AuditLogResponseDTO.fromEntity(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogResponseDTO recordAudit(
            String domain,
            String entityName,
            UUID entityId,
            String operation,
            String result,
            String actorEmail,
            UUID branchId,
            String ipAddress,
            String description,
            Object oldDataObj,
            Object newDataObj
    ) {
        User actor = null;
        if (actorEmail != null && !actorEmail.isBlank() && !"anonymousUser".equalsIgnoreCase(actorEmail)) {
            actor = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(actorEmail).orElse(null);
        }

        Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findById(branchId).orElse(null);
        }

        String oldJson = toJson(oldDataObj);
        String newJson = toJson(newDataObj);

        AuditLog log = new AuditLog(
                domain != null ? domain : "SYSTEM",
                entityName != null ? entityName : "general",
                entityId,
                operation != null ? operation : "UPDATE",
                result != null ? result : "SUCCESS",
                actor,
                branch,
                ipAddress,
                description,
                oldJson,
                newJson
        );

        AuditLog saved = auditLogRepository.save(log);
        LOG.debug("AuditLog registrado: [{}] {} on {} by {}",
                saved.getOperation(), saved.getResult(), saved.getEntityName(), actorEmail);

        return AuditLogResponseDTO.fromEntity(saved);
    }

    @Transactional
    public AuditLogResponseDTO recordLog(RecordAuditLogRequestDTO request) {
        User actor = null;
        if (request.actorUserId() != null) {
            actor = userRepository.findById(request.actorUserId()).orElse(null);
        }

        Branch branch = null;
        if (request.branchId() != null) {
            branch = branchRepository.findById(request.branchId()).orElse(null);
        }

        AuditLog log = new AuditLog(
                request.domainName(),
                request.entityName(),
                request.entityId(),
                request.operation(),
                request.result() != null ? request.result() : "SUCCESS",
                actor,
                branch,
                request.ipAddress(),
                request.description(),
                request.oldData(),
                request.newData()
        );

        AuditLog saved = auditLogRepository.save(log);
        return AuditLogResponseDTO.fromEntity(saved);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String str) return str;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
