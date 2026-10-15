package com.drivique.api.service;

import com.drivique.api.dto.ReportHistoryDTO;
import com.drivique.api.dto.ReportHistoryRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.AdministrativeReportType;
import com.drivique.api.model.AuditLog;
import com.drivique.api.model.GeneratedReport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.AdministrativeReportTypeRepository;
import com.drivique.api.repository.AuditLogRepository;
import com.drivique.api.repository.GeneratedReportRepository;
import com.drivique.api.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historial persistente de los reportes administrativos generados desde el panel. */
@Service
public class ReportHistoryService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final GeneratedReportRepository reports;
    private final AdministrativeReportTypeRepository types;
    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final ObjectMapper mapper;

    public ReportHistoryService(GeneratedReportRepository reports, AdministrativeReportTypeRepository types,
                                UserRepository users, AuditLogRepository auditLogs, ObjectMapper mapper) {
        this.reports = reports;
        this.types = types;
        this.users = users;
        this.auditLogs = auditLogs;
        this.mapper = mapper;
    }

    /** Un super administrador ve todos los reportes; el resto, solo los que generó. */
    @Transactional(readOnly = true)
    public List<ReportHistoryDTO> list(String email) {
        User actor = actor(email);
        List<GeneratedReport> visible = isSuperAdmin(actor)
                ? reports.findAllByOrderByGeneratedAtDesc()
                : reports.findByGeneratedByIdOrderByGeneratedAtDesc(actor.getId());
        return visible.stream().filter(report -> report.getSnapshot() != null).map(report -> toDto(report, false)).toList();
    }

    @Transactional
    public ReportHistoryDTO get(UUID id, String email) {
        User actor = actor(email);
        GeneratedReport report = reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado"));
        ensureAccess(report, actor);
        audit(actor, report, "UPDATE", "Descargó el reporte " + report.getReportCode() + " desde el historial");
        return toDto(report, true);
    }

    @Transactional
    public ReportHistoryDTO save(ReportHistoryRequestDTO request, String email) {
        User actor = actor(email);
        String typeCode = request.typeCode().toUpperCase();
        AdministrativeReportType type = types.findByCodeIgnoreCase(typeCode)
                .orElseGet(() -> types.save(new AdministrativeReportType(typeCode, request.typeName(), request.title(), true)));

        GeneratedReport report = new GeneratedReport(type, request.format(), actor,
                json(request.filters() == null ? java.util.Map.of() : request.filters()), null, "COMPLETED");
        report.setTitle(request.title());
        report.setTotalRecords(request.totalRecords());
        report.setSnapshot(json(request.snapshot()));
        report.setReportCode(nextCode());
        GeneratedReport saved = reports.save(report);
        audit(actor, saved, "INSERT", "Generó el reporte " + saved.getReportCode() + " (" + request.title() + ") en formato "
                + request.format() + " con " + request.totalRecords() + " registros");
        return toDto(saved, false);
    }

    @Transactional
    public void delete(UUID id, String email) {
        User actor = actor(email);
        GeneratedReport report = reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado"));
        ensureAccess(report, actor);
        audit(actor, report, "DELETE", "Eliminó el reporte " + report.getReportCode() + " del historial");
        reports.delete(report);
    }

    @Transactional
    public int clear(String email) {
        User actor = actor(email);
        List<GeneratedReport> visible = isSuperAdmin(actor)
                ? reports.findAllByOrderByGeneratedAtDesc()
                : reports.findByGeneratedByIdOrderByGeneratedAtDesc(actor.getId());
        reports.deleteAll(visible);
        auditLogs.save(new AuditLog("REPORTS", "GeneratedReport", actor.getId(), "DELETE", "SUCCESS", actor, null, null,
                "Vació el historial de reportes (" + visible.size() + " registros)", null, null));
        return visible.size();
    }

    private String nextCode() {
        String day = LocalDate.now(ZONE).toString().replace("-", "");
        long sequence = reports.count() + 1;
        String code = "REP-" + day + "-" + String.format("%04d", sequence);
        while (reports.existsByReportCode(code)) {
            sequence++;
            code = "REP-" + day + "-" + String.format("%04d", sequence);
        }
        return code;
    }

    private void ensureAccess(GeneratedReport report, User actor) {
        if (!isSuperAdmin(actor) && !report.getGeneratedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException("No tienes acceso a este reporte");
        }
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream().map(Role::getCode).anyMatch("SUPER_ADMIN"::equals);
    }

    private User actor(String email) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private void audit(User actor, GeneratedReport report, String action, String description) {
        auditLogs.save(new AuditLog("REPORTS", "GeneratedReport", report.getId(), action, "SUCCESS", actor, null, null,
                description, null, null));
    }

    private ReportHistoryDTO toDto(GeneratedReport report, boolean withSnapshot) {
        User author = report.getGeneratedBy();
        String role = author.getRoles().stream().map(Role::getCode).min(Comparator.naturalOrder()).orElse(null);
        return new ReportHistoryDTO(report.getId(), report.getReportCode(), report.getTitle(), report.getReportType().getCode(),
                report.getReportType().getName(), report.getFormat(), report.getTotalRecords(), parse(report.getFilters()),
                withSnapshot ? parse(report.getSnapshot()) : null, report.getGeneratedAt(), author.getFullName(),
                author.getEmail(), role);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El contenido del reporte no es válido");
        }
    }

    private Object parse(String json) {
        try {
            return json == null ? null : mapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
