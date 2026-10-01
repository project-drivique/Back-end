package com.drivique.api.service;

import com.drivique.api.model.User;
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
    private final FileStorageService fileStorageService;

    public UserDocumentService(
            UserDocumentRepository userDocumentRepository,
            DocumentTypeRepository documentTypeRepository,
            DocumentStatusRepository documentStatusRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService
    ) {
        this.userDocumentRepository = userDocumentRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStatusRepository = documentStatusRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
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
                .map(UserDocumentResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public UserDocumentResponseDTO uploadDocument(
            String userEmail,
            UUID documentTypeId,
            String documentNumber,
            MultipartFile frontFile,
            MultipartFile backFile
    ) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + userEmail));

        DocumentType docType = documentTypeRepository.findById(documentTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de documento no encontrado con id: " + documentTypeId));

        if (docType.isRequiresFrontAndBack() && (backFile == null || backFile.isEmpty())) {
            throw new IllegalArgumentException("El tipo de documento '" + docType.getName() + "' requiere adjuntar tanto el anverso como el reverso.");
        }

        String frontUrl = fileStorageService.storeFile(frontFile, "kyc");
        String backUrl = (backFile != null && !backFile.isEmpty()) ? fileStorageService.storeFile(backFile, "kyc") : null;

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

        if (documentNumber != null && !documentNumber.isBlank() && (user.getDocumentNumber() == null || user.getDocumentNumber().isBlank())) {
            user.setDocumentNumber(documentNumber);
            user.setDocumentTypeId(docType.getId());
            userRepository.save(user);
        }

        UserDocument saved = userDocumentRepository.save(doc);
        updateUserProfileCompletion(user);

        return UserDocumentResponseDTO.fromEntity(saved);
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

        String statusCode = request.status().toUpperCase();
        DocumentStatus newStatus = getOrCreateStatus(statusCode, statusCode.equals("APPROVED") ? "Aprobado" : "Rechazado");

        doc.setStatus(newStatus);
        doc.setReviewNotes(request.reviewNotes());
        doc.setReviewedBy(reviewer);
        doc.setReviewedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        UserDocument saved = userDocumentRepository.save(doc);

        updateUserProfileCompletion(doc.getUser());

        return UserDocumentResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<UserDocumentResponseDTO> getDocuments(String statusCode) {
        List<UserDocument> docs;
        if (statusCode != null && !statusCode.isBlank()) {
            docs = userDocumentRepository.findByStatus_CodeOrderByCreatedAtDesc(statusCode.toUpperCase());
        } else {
            docs = userDocumentRepository.findAllByOrderByCreatedAtDesc();
        }
        return docs.stream()
                .map(UserDocumentResponseDTO::fromEntity)
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
