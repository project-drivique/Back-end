package com.drivique.api.kyc.service;

import com.drivique.api.service.*;

import com.drivique.api.model.Branch;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.dto.ReviewDocumentRequestDTO;
import com.drivique.api.dto.UserDocumentResponseDTO;
import com.drivique.api.model.DocumentStatus;
import com.drivique.api.model.DocumentType;
import com.drivique.api.model.UserDocument;
import com.drivique.api.repository.DocumentStatusRepository;
import com.drivique.api.repository.DocumentTypeRepository;
import com.drivique.api.repository.UserDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDocumentServiceTests {

    @Mock
    private UserDocumentRepository userDocumentRepository;

    @Mock
    private DocumentTypeRepository documentTypeRepository;

    @Mock
    private DocumentStatusRepository documentStatusRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BranchUserRepository branchUserRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private NotificationService notificationService;

    private UserDocumentService userDocumentService;

    private User user;
    private User reviewer;
    private Branch branch;
    private DocumentType docType;
    private DocumentStatus pendingStatus;
    private DocumentStatus approvedStatus;

    @BeforeEach
    void setUp() {
        userDocumentService = new UserDocumentService(
                userDocumentRepository,
                documentTypeRepository,
                documentStatusRepository,
                userRepository,
                branchRepository,
                branchUserRepository,
                fileStorageService,
                notificationService
        );

        user = new User("John", "Doe", "john.doe@example.com", "hash");
        user.setId(UUID.randomUUID());

        reviewer = new User("Auditor", "Agent", "agent@drivique.com", "hash");
        reviewer.setId(UUID.randomUUID());

        branch = mock(Branch.class);
        lenient().when(branch.getId()).thenReturn(UUID.randomUUID());
        lenient().when(branchRepository.findById(any())).thenReturn(Optional.of(branch));

        docType = new DocumentType(UUID.randomUUID(), "CC", "Cédula de Ciudadanía", "Cédula", true, true, true);
        pendingStatus = new DocumentStatus(UUID.randomUUID(), "PENDING", "Pendiente", "Pendiente");
        approvedStatus = new DocumentStatus(UUID.randomUUID(), "APPROVED", "Aprobado", "Aprobado");
    }

    @Test
    void uploadDocumentRequiresBackFileWhenDocumentTypeDemandsIt() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));
        when(documentTypeRepository.findById(docType.getId())).thenReturn(Optional.of(docType));

        MockMultipartFile front = new MockMultipartFile("frontFile", "front.jpg", "image/jpeg", "content".getBytes());

        assertThatThrownBy(() -> userDocumentService.uploadDocument(user.getEmail(), docType.getId(), "123456", branch.getId(), front, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reverso");
    }

    @Test
    void uploadDocumentSuccessfullySavesNewPendingDocument() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));
        when(documentTypeRepository.findById(docType.getId())).thenReturn(Optional.of(docType));
        when(fileStorageService.storeFile(any(), eq("kyc"))).thenReturn("/uploads/kyc/test.jpg");
        when(documentStatusRepository.findByCode("PENDING")).thenReturn(Optional.of(pendingStatus));
        when(userDocumentRepository.findByUserIdAndDocumentType_Id(user.getId(), docType.getId())).thenReturn(Optional.empty());
        when(userDocumentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile front = new MockMultipartFile("frontFile", "front.jpg", "image/jpeg", "content".getBytes());
        MockMultipartFile back = new MockMultipartFile("backFile", "back.jpg", "image/jpeg", "content".getBytes());

        UserDocumentResponseDTO response = userDocumentService.uploadDocument(user.getEmail(), docType.getId(), "10203040", branch.getId(), front, back);

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo("PENDING");
        assertThat(response.documentNumber()).isEqualTo("10203040");
        verify(userDocumentRepository).save(any(UserDocument.class));
    }

    @Test
    void reviewDocumentApprovesAndMarksProfileCompleteWhenAllMandatoryTypesApproved() {
        UUID docId = UUID.randomUUID();
        UserDocument doc = new UserDocument(user, docType, pendingStatus, "10203040", "/uploads/kyc/front.jpg", "/uploads/kyc/back.jpg");
        doc.setId(docId);

        when(userDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(reviewer.getEmail())).thenReturn(Optional.of(reviewer));
        when(documentStatusRepository.findByCode("APPROVED")).thenReturn(Optional.of(approvedStatus));
        when(userDocumentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(documentTypeRepository.findByMandatoryTrueAndActiveTrue()).thenReturn(List.of(docType));
        when(userDocumentRepository.findByUserId(user.getId())).thenReturn(List.of(doc));

        ReviewDocumentRequestDTO request = new ReviewDocumentRequestDTO("APPROVED", "All clear");
        UserDocumentResponseDTO response = userDocumentService.reviewDocument(docId, reviewer.getEmail(), request);

        assertThat(response.statusCode()).isEqualTo("APPROVED");
        assertThat(doc.getReviewNotes()).isEqualTo("All clear");
        assertThat(user.isProfileComplete()).isTrue();
        verify(userRepository).save(user);
    }
}
