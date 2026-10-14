package com.drivique.api.service;

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
}
