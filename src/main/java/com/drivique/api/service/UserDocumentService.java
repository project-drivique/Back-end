package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.model.User;
import com.drivique.api.model.Branch;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.dto.DocumentTypeResponseDTO;
import com.drivique.api.dto.ReviewDocumentRequestDTO;
import com.drivique.api.dto.UserDocumentResponseDTO;
import com.drivique.api.model.DocumentStatus;
import com.drivique.api.model.DocumentType;
import com.drivique.api.model.UserDocument;
import com.drivique.api.repository.DocumentStatusRepository;
import com.drivique.api.repository.DocumentTypeRepository;
import com.drivique.api.repository.UserDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserDocumentService {

    private final UserDocumentRepository userDocumentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStatusRepository documentStatusRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final BranchUserRepository branchUserRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final ReservationDocumentReviewService reservationDocumentReviewService;
    private final com.drivique.api.repository.ReservationRepository reservationRepository;

    public UserDocumentService(
            UserDocumentRepository userDocumentRepository,
            DocumentTypeRepository documentTypeRepository,
            DocumentStatusRepository documentStatusRepository,
            UserRepository userRepository,
            BranchRepository branchRepository,
            BranchUserRepository branchUserRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService
    ) {
        this(userDocumentRepository, documentTypeRepository, documentStatusRepository, userRepository,
                branchRepository, branchUserRepository, fileStorageService, notificationService, null, null);
    }

    public UserDocumentService(
            UserDocumentRepository userDocumentRepository,
            DocumentTypeRepository documentTypeRepository,
            DocumentStatusRepository documentStatusRepository,
            UserRepository userRepository,
            BranchRepository branchRepository,
            BranchUserRepository branchUserRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            ReservationDocumentReviewService reservationDocumentReviewService
    ) {
        this(userDocumentRepository, documentTypeRepository, documentStatusRepository, userRepository,
                branchRepository, branchUserRepository, fileStorageService, notificationService, reservationDocumentReviewService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UserDocumentService(
            UserDocumentRepository userDocumentRepository,
            DocumentTypeRepository documentTypeRepository,
            DocumentStatusRepository documentStatusRepository,
            UserRepository userRepository,
            BranchRepository branchRepository,
            BranchUserRepository branchUserRepository,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ReservationDocumentReviewService reservationDocumentReviewService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.drivique.api.repository.ReservationRepository reservationRepository
    ) {
        this.userDocumentRepository = userDocumentRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStatusRepository = documentStatusRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.branchUserRepository = branchUserRepository;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.reservationDocumentReviewService = reservationDocumentReviewService;
        this.reservationRepository = reservationRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditService auditService;

    public record DecryptedDocumentFile(byte[] content, String contentType, String filename) {}

    private UserDocumentResponseDTO mapWithReservation(UserDocument doc) {
        String resCode = null;
        String vehName = null;
        if (doc.getUser() != null && reservationRepository != null) {
            var resList = reservationRepository.findByCustomerOrderByCreatedAtDesc(doc.getUser());
            if (!resList.isEmpty()) {
                var topRes = resList.get(0);
                resCode = topRes.getCode();
                if (topRes.getVehicle() != null) {
                    var v = topRes.getVehicle();
                    vehName = (v.getBrand() != null ? v.getBrand().getName() + " " : "") + v.getModel();
                }
            }
        }
        return UserDocumentResponseDTO.fromEntity(doc, resCode, vehName);
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponseDTO> getDocumentTypes() {
        return documentTypeRepository.findByActiveTrue().stream()
                .map(DocumentTypeResponseDTO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserDocumentResponseDTO> getUserDocuments(String userEmail) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + userEmail));

        return userDocumentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::mapWithReservation)
                .toList();
    }

    @Transactional
    public UserDocumentResponseDTO uploadDocument(
            String userEmail,
            UUID documentTypeId,
            String documentNumber,
            UUID branchId,
            MultipartFile frontFile,
            MultipartFile backFile
    ) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + userEmail));

        DocumentType docType = documentTypeRepository.findById(documentTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento no encontrado con id: " + documentTypeId));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada con id: " + branchId));

        boolean frontIsPdf = frontFile != null && ("application/pdf".equalsIgnoreCase(frontFile.getContentType())
                || frontFile.getOriginalFilename() != null && frontFile.getOriginalFilename().toLowerCase().endsWith(".pdf"));
        if (docType.isRequiresFrontAndBack() && (backFile == null || backFile.isEmpty()) && !frontIsPdf) {
            throw new IllegalArgumentException("El tipo de documento '" + docType.getName() + "' requiere adjuntar tanto el anverso como el reverso.");
        }

        String frontUrl = fileStorageService.storeFile(frontFile, "kyc");
        // Un único PDF puede contener anverso y reverso; se conserva el mismo
        // archivo para ambas referencias y se satisface la regla de integridad.
        String backUrl = (backFile != null && !backFile.isEmpty())
                ? fileStorageService.storeFile(backFile, "kyc")
                : (docType.isRequiresFrontAndBack() && frontIsPdf ? frontUrl : null);

        DocumentStatus pendingStatus = getOrCreateStatus("PENDING", "Pendiente de Revisión");

        Optional<UserDocument> existingDocOpt = userDocumentRepository.findByUserIdAndDocumentType_Id(user.getId(), docType.getId());
        UserDocument doc;
        if (existingDocOpt.isPresent()) {
            doc = existingDocOpt.get();
            doc.setDocumentNumber(documentNumber);
            doc.setFrontUrl(frontUrl);
            doc.setBackUrl(backUrl);
            doc.setStatus(pendingStatus);
            doc.setReviewNotes(null);
            doc.setReviewedBy(null);
            doc.setReviewedAt(null);
            doc.setUpdatedAt(Instant.now());
        } else {
            doc = new UserDocument(user, docType, pendingStatus, documentNumber, frontUrl, backUrl);
        }
        doc.setBranch(branch);

        user.setProfileComplete(false);
        user.setUpdatedAt(Instant.now());
        if (documentNumber != null && !documentNumber.isBlank() && (user.getDocumentNumber() == null || user.getDocumentNumber().isBlank())) {
            user.setDocumentNumber(documentNumber);
            user.setDocumentTypeId(docType.getId());
        }
        userRepository.save(user);

        UserDocument saved = userDocumentRepository.save(doc);
        updateUserProfileCompletion(user);

        return mapWithReservation(saved);
    }

    @Transactional
    @Auditable(domain = "KYC", entity = "UserDocument", action = "UPDATE", description = "Revisión de documento de identidad o licencia", includeResult = false)
    public UserDocumentResponseDTO reviewDocument(
            UUID documentId,
            String reviewerEmail,
            ReviewDocumentRequestDTO request
    ) {
        UserDocument doc = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado con id: " + documentId));

        User reviewer = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(reviewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Revisor no encontrado con email: " + reviewerEmail));
        branchUserRepository.findByUserId(reviewer.getId()).ifPresent(assignment -> {
            if (doc.getBranch() == null || !assignment.getBranchId().equals(doc.getBranch().getId())) {
                throw new IllegalArgumentException("No puedes revisar documentos de otra sucursal");
            }
        });

        String statusCode = request.status().toUpperCase();
        DocumentStatus newStatus = getOrCreateStatus(statusCode, statusCode.equals("APPROVED") ? "Aprobado" : "Rechazado");

        doc.setStatus(newStatus);
        doc.setReviewNotes(request.reviewNotes());
        doc.setReviewedBy(reviewer);
        doc.setReviewedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        UserDocument saved = userDocumentRepository.save(doc);

        updateUserProfileCompletion(doc.getUser());

        boolean approved = "APPROVED".equals(statusCode);
        String subject = approved ? "Documento aprobado" : "Documento rechazado";
        String message = approved
                ? "Tu documento " + doc.getDocumentType().getName() + " fue aprobado por " + (doc.getBranch() != null ? doc.getBranch().getName() : "la sucursal") + ". Ya puedes continuar con tu reserva cuando toda tu documentación esté aprobada."
                : "Tu documento " + doc.getDocumentType().getName() + " fue rechazado. Motivo: " + (request.reviewNotes() == null || request.reviewNotes().isBlank() ? "Debes cargar nuevamente un documento válido." : request.reviewNotes());
        String docKey = "DOC-" + saved.getId() + "-" + statusCode;
        notificationService.send(doc.getUser(), "EMAIL", approved ? "DOCUMENT_APPROVED" : "DOCUMENT_REJECTED", subject, message, saved.getId(), docKey + "-EMAIL");

        try {
            notificationService.emitDomainEvent(
                    doc.getUser(),
                    "DOCUMENT",
                    "NORMAL",
                    saved.getId(),
                    java.util.Map.of("documentType", doc.getDocumentType() != null ? doc.getDocumentType().getName() : "Documento",
                            "status", approved ? "Aprobado" : "Rechazado"),
                    "DOC-" + saved.getId() + "-" + statusCode
            );
        } catch (Exception e) {}

        if (approved && reservationDocumentReviewService != null) {
            reservationDocumentReviewService.onDocumentsReviewed(doc.getUser());
        }

        return mapWithReservation(saved);
    }

    @Transactional(readOnly = true)
    public List<UserDocumentResponseDTO> getDocuments(String statusCode, String reviewerEmail) {
        List<UserDocument> docs;
        User reviewer = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(reviewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Revisor no encontrado con email: " + reviewerEmail));
        var branchAssignment = branchUserRepository.findByUserId(reviewer.getId());
        if (branchAssignment.isPresent()) {
            UUID branchId = branchAssignment.get().getBranchId();
            docs = statusCode != null && !statusCode.isBlank()
                    ? userDocumentRepository.findByBranch_IdAndStatus_CodeOrderByCreatedAtDesc(branchId, statusCode.toUpperCase())
                    : userDocumentRepository.findByBranch_IdOrderByCreatedAtDesc(branchId);
        } else if (statusCode != null && !statusCode.isBlank()) {
            docs = userDocumentRepository.findByStatus_CodeOrderByCreatedAtDesc(statusCode.toUpperCase());
        } else {
            docs = userDocumentRepository.findAllByOrderByCreatedAtDesc();
        }
        return docs.stream()
                .map(this::mapWithReservation)
                .toList();
    }

    private DocumentStatus getOrCreateStatus(String code, String defaultName) {
        return documentStatusRepository.findByCode(code)
                .orElseGet(() -> documentStatusRepository.save(new DocumentStatus(code, defaultName, defaultName)));
    }

    private void updateUserProfileCompletion(User user) {
        List<DocumentType> mandatoryTypes = documentTypeRepository.findByMandatoryTrueAndActiveTrue();
        if (mandatoryTypes.isEmpty()) {
            return;
        }

        List<UserDocument> userDocs = userDocumentRepository.findByUserId(user.getId());
        Set<UUID> approvedDocTypeIds = userDocs.stream()
                .filter(d -> d.getStatus() != null && "APPROVED".equalsIgnoreCase(d.getStatus().getCode()))
                .map(d -> d.getDocumentType().getId())
                .collect(Collectors.toSet());

        boolean allMandatoryApproved = mandatoryTypes.stream()
                .allMatch(type -> approvedDocTypeIds.contains(type.getId()));

        if (user.isProfileComplete() != allMandatoryApproved) {
            user.setProfileComplete(allMandatoryApproved);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        }
    }

    @Transactional(readOnly = true)
    public DecryptedDocumentFile getDocumentFile(UUID documentId, String side, String requesterEmail, String clientIp) {
        UserDocument doc = userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento no encontrado con id: " + documentId));

        User requester = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario solicitante no encontrado con email: " + requesterEmail));

        boolean isOwner = doc.getUser() != null && doc.getUser().getId().equals(requester.getId());

        if (!isOwner) {
            boolean isStaff = requester.getRoles() != null && requester.getRoles().stream()
                    .anyMatch(r -> Set.of("SUPER_ADMIN", "ADMIN", "BRANCH_ADMIN", "EMPLOYEE")
                            .contains(r.getCode().toUpperCase()));

            if (!isStaff && requester.getRoles() != null) {
                isStaff = requester.getRoles().stream()
                        .flatMap(r -> r.getPermissions().stream())
                        .anyMatch(p -> "kyc:review".equalsIgnoreCase(p.getCode()));
            }

            if (!isStaff) {
                throw new org.springframework.security.access.AccessDeniedException("No tienes permisos para visualizar este documento confidencial.");
            }

            if (branchUserRepository != null) {
                branchUserRepository.findByUserId(requester.getId()).ifPresent(assignment -> {
                    if (doc.getBranch() != null && !assignment.getBranchId().equals(doc.getBranch().getId())) {
                        throw new org.springframework.security.access.AccessDeniedException("No tienes permisos para consultar documentos de otra sucursal.");
                    }
                });
            }

            if (auditService != null) {
                String docTypeName = doc.getDocumentType() != null ? doc.getDocumentType().getName() : "Documento";
                String customerEmail = doc.getUser() != null ? doc.getUser().getEmail() : "N/A";
                auditService.recordAudit(
                        "KYC",
                        "UserDocument",
                        doc.getId(),
                        "VIEW_DOCUMENT",
                        "SUCCESS",
                        requesterEmail,
                        doc.getBranch() != null ? doc.getBranch().getId() : null,
                        clientIp,
                        "Acceso y desencriptación en memoria de documento confidencial KYC (" + docTypeName + ", lado: " + (side != null ? side : "front") + ") del usuario " + customerEmail,
                        null,
                        null
                );
            }
        }

        String targetPath = ("back".equalsIgnoreCase(side) && doc.getBackUrl() != null && !doc.getBackUrl().isBlank())
                ? doc.getBackUrl()
                : doc.getFrontUrl();

        if (targetPath == null || targetPath.isBlank()) {
            throw new ResourceNotFoundException("El documento no contiene el archivo solicitado (" + side + ").");
        }

        byte[] decryptedBytes = fileStorageService.read(targetPath);
        String contentType = detectContentType(targetPath);
        String docTypeName = (doc.getDocumentType() != null ? doc.getDocumentType().getName() : "documento")
                .toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        String extension = targetPath.toLowerCase().endsWith(".pdf") ? ".pdf"
                : targetPath.toLowerCase().endsWith(".png") ? ".png" : ".jpg";
        String filename = docTypeName + "-" + (side != null ? side : "front") + extension;

        return new DecryptedDocumentFile(decryptedBytes, contentType, filename);
    }

    @Transactional(readOnly = true)
    public DecryptedDocumentFile getDocumentFileByPath(String storedPath, String requesterEmail, String clientIp) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new IllegalArgumentException("Ruta de archivo no especificada.");
        }

        Optional<UserDocument> docOpt = userDocumentRepository.findFirstByFrontUrlOrBackUrl(storedPath, storedPath);
        if (docOpt.isPresent()) {
            UserDocument doc = docOpt.get();
            String side = storedPath.equals(doc.getBackUrl()) ? "back" : "front";
            return getDocumentFile(doc.getId(), side, requesterEmail, clientIp);
        }

        User requester = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario solicitante no encontrado con email: " + requesterEmail));

        boolean isStaff = requester.getRoles() != null && requester.getRoles().stream()
                .anyMatch(r -> Set.of("SUPER_ADMIN", "ADMIN", "BRANCH_ADMIN", "EMPLOYEE")
                        .contains(r.getCode().toUpperCase()));

        if (!isStaff) {
            throw new org.springframework.security.access.AccessDeniedException("No tienes permisos para visualizar este archivo confidencial.");
        }

        byte[] decryptedBytes = fileStorageService.read(storedPath);
        String contentType = detectContentType(storedPath);
        String filename = "documento-kyc" + (storedPath.toLowerCase().endsWith(".pdf") ? ".pdf" : ".jpg");
        return new DecryptedDocumentFile(decryptedBytes, contentType, filename);
    }

    private String detectContentType(String path) {
        if (path == null) return "application/octet-stream";
        String lower = path.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
