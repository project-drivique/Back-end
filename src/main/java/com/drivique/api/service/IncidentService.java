package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.IncidentAssignRequestDTO;
import com.drivique.api.dto.IncidentDTO;
import com.drivique.api.dto.IncidentRequestDTO;
import com.drivique.api.dto.IncidentStatusRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentService {

    private static final Logger LOG = LoggerFactory.getLogger(IncidentService.class);
    private static final Set<String> UNRESTRICTED_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "AGENT");
    private static final Set<String> BRANCH_ROLES = Set.of("EMPLOYEE", "BRANCH_ADMIN");
    private static final int MAX_FILES_PER_REQUEST = 5;
    private static final long MAX_ATTACHMENTS_PER_INCIDENT = 20;

    private final IncidentReportRepository reports;
    private final IncidentResponseRepository responses;
    private final IncidentAttachmentRepository attachments;
    private final IncidentStatusHistoryRepository history;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ReservationRepository reservations;
    private final BranchRepository branches;
    private final BranchUserRepository branchUsers;
    private final FileStorageService storage;
    private final NotificationService notificationService;

    @Autowired
    public IncidentService(IncidentReportRepository reports, IncidentResponseRepository responses,
            IncidentAttachmentRepository attachments, IncidentStatusHistoryRepository history, UserRepository users,
            VehicleRepository vehicles, ReservationRepository reservations, BranchRepository branches,
            BranchUserRepository branchUsers, FileStorageService storage,
            @Autowired(required = false) NotificationService notificationService) {
        this.reports = reports;
        this.responses = responses;
        this.attachments = attachments;
        this.history = history;
        this.users = users;
        this.vehicles = vehicles;
        this.reservations = reservations;
        this.branches = branches;
        this.branchUsers = branchUsers;
        this.storage = storage;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ creación

    @Transactional
    @Auditable(domain = "SUPPORT", entity = "IncidentReport", action = "INSERT", description = "Registro de incidencia", includeArgs = false, includeResult = false)
    public IncidentDTO create(IncidentRequestDTO in, String email) {
        User user = current(email);
        Reservation reservation = null;
        if (in.reservationId() != null) {
            reservation = reservations.findById(in.reservationId())
                    .filter(r -> r.getCustomer().getId().equals(user.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        }
        Vehicle vehicle = null;
        if (in.vehicleId() != null) {
            vehicle = vehicles.findById(in.vehicleId()).orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado"));
        } else if (reservation != null) {
            vehicle = reservation.getVehicle();
        }
        if ("RESERVATION".equals(in.category()) && reservation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona la reserva a la que se refiere la incidencia.");
        }
        if ("VEHICLE".equals(in.category()) && vehicle == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona la reserva o el vehículo al que se refiere la incidencia.");
        }

        Branch branch = resolveBranch(in, reservation, vehicle);
        if (branch == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona la sede a la que va dirigida la incidencia.");
        }
        String statusCode = reservation != null && reservation.getStatus() != null ? reservation.getStatus().getCode() : null;
        IncidentWorkflow.Classification classification = IncidentWorkflow.classify(in.category(), in.subject() + " " + in.description(), statusCode);

        String prefix = "INC-" + Year.now().getValue() + "-";
        String code = prefix + String.format("%04d", reports.countByIncidentCodeStartingWith(prefix) + 1);
        IncidentReport report = new IncidentReport(code, vehicle, reservation, user, classification.priority(), in.subject().trim(), in.description().trim());
        report.classify(in.category(), classification.priority(), branch, Instant.now().plus(classification.sla()));
        IncidentReport saved = reports.saveAndFlush(report);
        history.save(new IncidentStatusHistory(saved, null, "OPEN", user, "Incidencia registrada"));

        notifyUser(user, "Incidencia " + code + " registrada",
                "Recibimos tu reporte \"" + saved.getSubject() + "\". Te avisaremos cuando el equipo lo atienda.", saved);
        notifyBranchStaff(saved, "Nueva incidencia " + code + " (" + classification.priority() + ")", saved.getSubject());
        return toDto(saved, user, true);
    }

    private Branch resolveBranch(IncidentRequestDTO in, Reservation reservation, Vehicle vehicle) {
        if (vehicle != null && vehicle.getCurrentBranch() != null) return vehicle.getCurrentBranch();
        if (reservation != null && reservation.getCashPaymentBranch() != null) return reservation.getCashPaymentBranch();
        if (in.branchId() != null) {
            return branches.findById(in.branchId()).orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada"));
        }
        return null;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<IncidentDTO> mine(String email) {
        User user = current(email);
        return reports.findByReporterIdOrderByCreatedAtDesc(user.getId()).stream().map(r -> toDto(r, user, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentDTO> forStaff(String email, String status, String priority) {
        User user = current(email);
        Optional<UUID> branchId = staffBranchScope(user);
        List<IncidentReport> found = branchId == null ? List.of()
                : branchId.isEmpty() ? reports.findAllByOrderByCreatedAtDesc()
                : reports.findByBranchIdOrderByCreatedAtDesc(branchId.get());
        return found.stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().equalsIgnoreCase(status))
                .filter(r -> priority == null || priority.isBlank() || r.getPriority().equalsIgnoreCase(priority))
                .map(r -> toDto(r, user, false)).toList();
    }

    @Transactional(readOnly = true)
    public IncidentDTO detail(UUID id, String email) {
        User user = current(email);
        IncidentReport report = load(id);
        assertAccess(user, report);
        return toDto(report, user, true);
    }

    // ------------------------------------------------------------------ adjuntos y conversación

    @Transactional
    public IncidentDTO addAttachments(UUID id, List<MultipartFile> files, String email) {
        User user = current(email);
        IncidentReport report = load(id);
        assertAccess(user, report);
        assertNotClosed(report);
        storeFiles(report, null, user, files);
        report.touch();
        return toDto(report, user, true);
    }

    @Transactional
    @Auditable(domain = "SUPPORT", entity = "IncidentReport", action = "UPDATE", description = "Respuesta en una incidencia", includeArgs = false, includeResult = false)
    public IncidentDTO respond(UUID id, String message, List<MultipartFile> files, String email) {
        User user = current(email);
        IncidentReport report = load(id);
        assertAccess(user, report);
        assertNotClosed(report);
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje es obligatorio.");
        }
        boolean owner = isOwner(user, report);
        if (!owner && isGlobalAdmin(user) && !isBranchStaff(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El Administrador General solo tiene permisos de supervisión y lectura sobre las incidencias operativas.");
        }
        IncidentResponse saved = responses.saveAndFlush(new IncidentResponse(report, user, message.trim(), null));
        storeFiles(report, saved, user, files);

        if (owner && "RESOLVED".equals(report.getStatus())) {
            transition(report, "IN_REVIEW", user, "El cliente respondió y reabrió la incidencia.");
        }
        report.touch();

        if (owner) {
            if (report.getAssignedTo() != null) {
                notifyUser(report.getAssignedTo(), "Respuesta del cliente en " + report.getIncidentCode(), message.trim(), report);
            } else {
                notifyBranchStaff(report, "Respuesta del cliente en " + report.getIncidentCode(), message.trim());
            }
        } else {
            notifyUser(report.getReporter(), "Nueva respuesta en tu incidencia " + report.getIncidentCode(), message.trim(), report);
        }
        return toDto(report, user, true);
    }

    @Transactional(readOnly = true)
    public IncidentAttachment attachment(UUID incidentId, UUID attachmentId, String email) {
        User user = current(email);
        IncidentReport report = load(incidentId);
        assertAccess(user, report);
        IncidentAttachment found = attachments.findById(attachmentId)
                .filter(a -> a.getIncident().getId().equals(incidentId))
                .orElseThrow(() -> new ResourceNotFoundException("Adjunto no encontrado"));
        return found;
    }

    public byte[] readAttachment(IncidentAttachment attachment) {
        return storage.read(attachment.getStoragePath());
    }

    // ------------------------------------------------------------------ estado y asignación

    @Transactional
    @Auditable(domain = "SUPPORT", entity = "IncidentReport", action = "UPDATE", description = "Cambio de estado de incidencia", includeResult = false)
    public IncidentDTO changeStatus(UUID id, IncidentStatusRequestDTO input, String email) {
        User user = current(email);
        IncidentReport report = load(id);
        assertAccess(user, report);
        boolean staff = !isOwner(user, report);
        String target = input.status();
        if (!IncidentWorkflow.canTransition(report.getStatus(), target, staff)) {
            throw new ConflictException("Transición no permitida: " + report.getStatus() + " a " + target);
        }
        if (staff) {
            assertCanOperate(user, report);
            if (report.getAssignedTo() == null) {
                report.assignTo(user);
                history.save(new IncidentStatusHistory(report, report.getStatus(), report.getStatus(), user,
                        "Asignada a " + user.getFullName()));
            }
        }
        transition(report, target, user, input.note());
        notifyUser(report.getReporter(), "Tu incidencia " + report.getIncidentCode() + " cambió de estado",
                "Nuevo estado: " + statusLabel(target) + (input.note() != null && !input.note().isBlank() ? ". " + input.note().trim() : "."), report);
        return toDto(report, user, true);
    }

    @Transactional
    @Auditable(domain = "SUPPORT", entity = "IncidentReport", action = "UPDATE", description = "Asignación de incidencia", includeResult = false)
    public IncidentDTO assign(UUID id, IncidentAssignRequestDTO input, String email) {
        User user = current(email);
        IncidentReport report = load(id);
        assertAccess(user, report);
        if (isGlobalAdmin(user) && !isBranchStaff(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El Administrador General solo tiene permisos de supervisión y lectura sobre las incidencias operativas.");
        }
        if (isOwner(user, report) && !isStaffUser(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para asignar incidencias.");
        }
        assertNotClosed(report);
        User operator = user;
        if (input != null && input.assigneeUserId() != null && !input.assigneeUserId().equals(user.getId())) {
            boolean manager = user.getRoles().stream().anyMatch(r -> "BRANCH_ADMIN".equals(r.getCode()) || UNRESTRICTED_ROLES.contains(r.getCode()));
            if (!manager) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador de sucursal puede asignar a otra persona.");
            }
            operator = users.findById(input.assigneeUserId()).filter(u -> u.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Operador no encontrado"));
            if (!canOperate(operator, report)) {
                throw new ConflictException("El operador no pertenece a la sede de la incidencia.");
            }
        }
        report.assignTo(operator);
        history.save(new IncidentStatusHistory(report, report.getStatus(), report.getStatus(), user, "Asignada a " + operator.getFullName()));
        notifyUser(report.getReporter(), "Tu incidencia " + report.getIncidentCode() + " fue asignada",
                operator.getFullName() + " atenderá tu caso.", report);
        return toDto(report, user, true);
    }

    private void transition(IncidentReport report, String target, User actor, String note) {
        String from = report.getStatus();
        report.changeStatus(target);
        history.save(new IncidentStatusHistory(report, from, target, actor, note == null || note.isBlank() ? null : note.trim()));
    }

    /** Un empleado solo puede eliminar un reporte interno propio mientras siga recibido; los de clientes nunca. */
    @Transactional
    @Auditable(domain = "SUPPORT", entity = "IncidentReport", action = "DELETE", description = "Eliminación de reporte interno de incidencia", includeArgs = false, includeResult = false)
    public void deleteOwnInternal(UUID id, String email) {
        User user = current(email);
        IncidentReport report = reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incidencia no encontrada"));
        if (isGlobalAdmin(user) && !isBranchStaff(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El Administrador General solo tiene permisos de supervisión y lectura sobre las incidencias operativas.");
        }
        if (!isOwner(user, report) || !isStaffUser(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Los reportes creados por los clientes no pueden ser eliminados por control de auditoría.");
        }
        if (!"OPEN".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar un reporte propio que ya pasó a revisión o resolución.");
        }
        // Primero lo que depende del reporte: adjuntos, mensajes e historial de estados
        attachments.deleteByIncidentId(report.getId());
        responses.deleteByIncidentId(report.getId());
        history.deleteByIncidentId(report.getId());
        reports.delete(report);
    }

    // ------------------------------------------------------------------ acceso

    private boolean isOwner(User user, IncidentReport report) {
        return report.getReporter().getId().equals(user.getId());
    }

    private boolean isStaffUser(User user) {
        return user.getRoles().stream().anyMatch(r -> UNRESTRICTED_ROLES.contains(r.getCode()) || BRANCH_ROLES.contains(r.getCode()));
    }

    private boolean isGlobalAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getCode()) || "SUPER_ADMIN".equals(r.getCode()));
    }

    private boolean isBranchStaff(User user) {
        return user.getRoles().stream().anyMatch(r -> "BRANCH_ADMIN".equals(r.getCode()) || "EMPLOYEE".equals(r.getCode()));
    }

    /** null = sin acceso de personal; vacío = todas las sedes; valor = solo esa sede. */
    private Optional<UUID> staffBranchScope(User user) {
        Set<String> codes = new java.util.HashSet<>();
        user.getRoles().forEach(r -> codes.add(r.getCode()));
        if (codes.stream().anyMatch(UNRESTRICTED_ROLES::contains)) return Optional.empty();
        if (codes.stream().anyMatch(BRANCH_ROLES::contains)) {
            return branchUsers.findByUserId(user.getId()).map(b -> Optional.of(b.getBranchId())).orElse(null);
        }
        return null;
    }

    private boolean canOperate(User user, IncidentReport report) {
        Optional<UUID> scope = staffBranchScope(user);
        if (scope == null) return false;
        if (scope.isEmpty()) return true;
        return report.getBranch() != null && scope.get().equals(report.getBranch().getId());
    }

    private void assertAccess(User user, IncidentReport report) {
        if (isOwner(user, report) || canOperate(user, report)) return;
        throw new ResourceNotFoundException("Incidencia no encontrada");
    }

    private void assertCanOperate(User user, IncidentReport report) {
        if (isGlobalAdmin(user) && !isBranchStaff(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El Administrador General solo tiene permisos de supervisión y lectura sobre las incidencias operativas.");
        }
        if (!canOperate(user, report)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para atender esta incidencia.");
        }
        boolean manager = user.getRoles().stream().anyMatch(r -> "BRANCH_ADMIN".equals(r.getCode()) || UNRESTRICTED_ROLES.contains(r.getCode()));
        if (report.getAssignedTo() != null && !report.getAssignedTo().getId().equals(user.getId()) && !manager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La incidencia está asignada a otro operador.");
        }
    }

    private void assertNotClosed(IncidentReport report) {
        if ("CLOSED".equals(report.getStatus())) {
            throw new ConflictException("La incidencia está cerrada.");
        }
    }

    // ------------------------------------------------------------------ utilidades

    private void storeFiles(IncidentReport report, IncidentResponse response, User user, List<MultipartFile> files) {
        List<MultipartFile> valid = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (valid.isEmpty()) return;
        if (valid.size() > MAX_FILES_PER_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_FILES_PER_REQUEST + " archivos por envío.");
        }
        if (attachments.countByIncidentId(report.getId()) + valid.size() > MAX_ATTACHMENTS_PER_INCIDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La incidencia alcanzó el máximo de adjuntos permitidos.");
        }
        for (MultipartFile file : valid) {
            storage.validateFile(file);
            String path = storage.storeFile(file, "support/incidents/" + report.getId());
            attachments.save(new IncidentAttachment(report, response, user,
                    file.getOriginalFilename() == null ? "adjunto" : file.getOriginalFilename(),
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType(), file.getSize(), path));
        }
    }

    private IncidentReport load(UUID id) {
        return reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incidencia no encontrada"));
    }

    private User current(String email) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "OPEN" -> "Abierta";
            case "IN_REVIEW" -> "En revisión";
            case "RESOLVED" -> "Resuelta";
            case "CLOSED" -> "Cerrada";
            default -> status;
        };
    }

    private void notifyUser(User target, String subject, String message, IncidentReport report) {
        if (notificationService == null || target == null) return;
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("incidentCode", report.getIncidentCode());
        metadata.put("incidentId", report.getId().toString());
        metadata.put("actionLabel", "Ver incidencia");
        metadata.put("actionPath", "/soporte?incidencia=" + report.getId());
        for (String channel : List.of("IN_APP", "EMAIL")) {
            try {
                notificationService.sendWithMetadata(target, channel, "GENERAL", subject, message, report.getId(), metadata,
                        "INC-" + report.getId() + "-" + System.nanoTime() + "-" + channel);
            } catch (Exception e) {
                LOG.warn("No se pudo notificar ({}) la incidencia {}: {}", channel, report.getIncidentCode(), e.getMessage());
            }
        }
    }

    private void notifyBranchStaff(IncidentReport report, String subject, String message) {
        if (notificationService == null || report.getBranch() == null) return;
        try {
            for (BranchUser assignment : branchUsers.findByBranchIdOrderByAssignedAtAsc(report.getBranch().getId())) {
                User staff = assignment.getUser();
                if (staff != null && !staff.getId().equals(report.getReporter().getId())) {
                    Map<String, Object> metadata = Map.of("incidentCode", report.getIncidentCode(), "incidentId", report.getId().toString(),
                            "actionLabel", "Ver incidencia", "actionPath", "/encargado/incidents");
                    notificationService.sendWithMetadata(staff, "IN_APP", "GENERAL", subject, message, report.getId(), metadata,
                            "INC-" + report.getId() + "-" + System.nanoTime() + "-STAFF-" + staff.getId());
                }
            }
        } catch (Exception e) {
            LOG.warn("No se pudo notificar a la sede de la incidencia {}: {}", report.getIncidentCode(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ mapeo

    private IncidentDTO.Attachment attachmentDto(IncidentAttachment a) {
        return new IncidentDTO.Attachment(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(), a.getCreatedAt());
    }

    private IncidentDTO toDto(IncidentReport r, User viewer, boolean withDetail) {
        boolean staffView = !isOwner(viewer, r);
        List<IncidentDTO.Attachment> initial = new ArrayList<>();
        List<IncidentDTO.TimelineEntry> timeline = new ArrayList<>();
        // Los adjuntos iniciales viajan siempre (el listado muestra las evidencias); la conversación solo en el detalle
        for (IncidentAttachment a : attachments.findByIncidentIdOrderByCreatedAtAsc(r.getId())) {
            if (a.getResponse() == null) initial.add(attachmentDto(a));
        }
        if (withDetail) {
            List<IncidentAttachment> all = attachments.findByIncidentIdOrderByCreatedAtAsc(r.getId());
            Map<UUID, List<IncidentDTO.Attachment>> byResponse = new HashMap<>();
            for (IncidentAttachment a : all) {
                if (a.getResponse() != null) byResponse.computeIfAbsent(a.getResponse().getId(), k -> new ArrayList<>()).add(attachmentDto(a));
            }
            for (IncidentResponse resp : responses.findByIncidentIdOrderByCreatedAtAsc(r.getId())) {
                boolean fromCustomer = resp.getAuthor().getId().equals(r.getReporter().getId());
                timeline.add(new IncidentDTO.TimelineEntry("MESSAGE", resp.getAuthor().getId(), resp.getAuthor().getFullName(),
                        fromCustomer ? "CUSTOMER" : "STAFF", resp.getMessage(), null, null,
                        byResponse.getOrDefault(resp.getId(), List.of()), resp.getCreatedAt()));
            }
            for (IncidentStatusHistory h : history.findByIncidentIdOrderByCreatedAtAsc(r.getId())) {
                boolean assignment = h.getFromStatus() != null && h.getFromStatus().equals(h.getToStatus());
                boolean fromCustomer = h.getChangedBy().getId().equals(r.getReporter().getId());
                timeline.add(new IncidentDTO.TimelineEntry(assignment ? "ASSIGNMENT" : "STATUS", h.getChangedBy().getId(),
                        h.getChangedBy().getFullName(), fromCustomer ? "CUSTOMER" : "STAFF", h.getNote(),
                        h.getFromStatus(), h.getToStatus(), List.of(), h.getCreatedAt()));
            }
            timeline.sort(Comparator.comparing(IncidentDTO.TimelineEntry::createdAt));
        }
        boolean open = "OPEN".equals(r.getStatus()) || "IN_REVIEW".equals(r.getStatus());
        Vehicle v = r.getVehicle();
        Reservation res = r.getReservation();
        boolean isReadOnlySupervisor = staffView && (isGlobalAdmin(viewer) && !isBranchStaff(viewer));
        List<String> allowedTransitions = isReadOnlySupervisor ? List.of()
                : IncidentWorkflow.allowedTransitions(r.getStatus(), staffView);
        return new IncidentDTO(r.getId(), r.getIncidentCode(), r.getCategory(), r.getStatus(), r.getPriority(), r.getSubject(),
                r.getDescription(), v != null ? v.getId() : null, v != null ? v.getPlate() : null, v != null ? v.getModel() : null,
                res != null ? res.getId() : null, res != null ? res.getCode() : null,
                r.getBranch() != null ? r.getBranch().getId() : null, r.getBranch() != null ? r.getBranch().getName() : null,
                r.getReporter().getId(), r.getReporter().getFullName(), r.getReporter().getEmail(), r.getReporter().getPhone(),
                isStaffUser(r.getReporter()),
                r.getAssignedTo() != null ? r.getAssignedTo().getId() : null, r.getAssignedTo() != null ? r.getAssignedTo().getFullName() : null,
                r.getSlaDueAt(), open && r.getSlaDueAt() != null && Instant.now().isAfter(r.getSlaDueAt()),
                r.getCreatedAt(), r.getUpdatedAt(), r.getResolvedAt(), r.getClosedAt(),
                allowedTransitions, initial, timeline);
    }
}
