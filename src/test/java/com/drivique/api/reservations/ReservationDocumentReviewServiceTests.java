package com.drivique.api.reservations;

import com.drivique.api.exception.ConflictException;
import com.drivique.api.model.*;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.ReservationStatusRepository;
import com.drivique.api.repository.UserDocumentRepository;
import com.drivique.api.service.NotificationService;
import com.drivique.api.service.ReservationDocumentReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationDocumentReviewServiceTests {

    @Mock
    private UserDocumentRepository userDocumentRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationStatusRepository reservationStatusRepository;

    @Mock
    private NotificationService notificationService;

    private ReservationDocumentReviewService reviewService;

    private User user;
    private DocumentType licenseType;
    private DocumentType ccType;
    private DocumentType passportType;
    private DocumentStatus approvedStatus;
    private DocumentStatus pendingStatus;
    private ReservationStatus pendingReviewStatus;
    private ReservationStatus pendingPaymentStatus;

    @BeforeEach
    void setUp() {
        reviewService = new ReservationDocumentReviewService(
                userDocumentRepository,
                reservationRepository,
                reservationStatusRepository,
                notificationService,
                "http://localhost:5173"
        );

        user = new User("Laura", "Admin", "laura@example.com", "hash");
        user.setId(UUID.randomUUID());

        licenseType = new DocumentType(UUID.randomUUID(), "DRIVER_LICENSE", "Licencia de Conducción", "Licencia", true, true, true);
        ccType = new DocumentType(UUID.randomUUID(), "CC", "Cédula de Ciudadanía", "Cédula", true, true, false);
        passportType = new DocumentType(UUID.randomUUID(), "PASSPORT", "Pasaporte", "Pasaporte", true, true, false);

        approvedStatus = new DocumentStatus(UUID.randomUUID(), "APPROVED", "Aprobado", "Aprobado");
        pendingStatus = new DocumentStatus(UUID.randomUUID(), "PENDING", "Pendiente", "Pendiente");

        pendingReviewStatus = new ReservationStatus("PENDING_DOCUMENT_REVIEW", "Pending review", true);
        pendingPaymentStatus = new ReservationStatus("PENDING_PAYMENT", "Pending payment", true);
    }

    @Test
    void hasApprovedRequiredDocuments_WhenNoDocuments_ReturnsFalse() {
        when(userDocumentRepository.findByUserId(user.getId())).thenReturn(Collections.emptyList());

        boolean result = reviewService.hasApprovedRequiredDocuments(user.getId());

        assertThat(result).isFalse();
    }

    @Test
    void hasApprovedRequiredDocuments_WhenOnlyDriverLicenseApproved_ReturnsFalse() {
        UserDocument licenseDoc = new UserDocument(user, licenseType, approvedStatus, "DL12345", "front.jpg", "back.jpg");
        when(userDocumentRepository.findByUserId(user.getId())).thenReturn(List.of(licenseDoc));

        boolean result = reviewService.hasApprovedRequiredDocuments(user.getId());

        assertThat(result).isFalse();
    }

    @Test
    void hasApprovedRequiredDocuments_WhenIdentityDocPending_ReturnsFalse() {
        UserDocument licenseDoc = new UserDocument(user, licenseType, approvedStatus, "DL12345", "front.jpg", "back.jpg");
        UserDocument ccDoc = new UserDocument(user, ccType, pendingStatus, "10203040", "front.jpg", "back.jpg");
        when(userDocumentRepository.findByUserId(user.getId())).thenReturn(List.of(licenseDoc, ccDoc));

        boolean result = reviewService.hasApprovedRequiredDocuments(user.getId());

        assertThat(result).isFalse();
    }

    @Test
    void hasApprovedRequiredDocuments_WhenLicenseAndCCApproved_ReturnsTrue() {
        UserDocument licenseDoc = new UserDocument(user, licenseType, approvedStatus, "DL12345", "front.jpg", "back.jpg");
        UserDocument ccDoc = new UserDocument(user, ccType, approvedStatus, "10203040", "front.jpg", "back.jpg");
        when(userDocumentRepository.findByUserId(user.getId())).thenReturn(List.of(licenseDoc, ccDoc));

        boolean result = reviewService.hasApprovedRequiredDocuments(user.getId());

        assertThat(result).isTrue();
    }

    @Test
    void hasApprovedRequiredDocuments_WhenLicenseAndPassportApproved_ReturnsTrue() {
        UserDocument licenseDoc = new UserDocument(user, licenseType, approvedStatus, "DL12345", "front.jpg", "back.jpg");
        UserDocument passportDoc = new UserDocument(user, passportType, approvedStatus, "PA12345", "front.jpg", null);
        when(userDocumentRepository.findByUserId(user.getId())).thenReturn(List.of(licenseDoc, passportDoc));

        boolean result = reviewService.hasApprovedRequiredDocuments(user.getId());

        assertThat(result).isTrue();
    }

    @Test
    void validateCanBePaid_WhenPendingDocumentReview_DoesNotThrow() {
        Vehicle vehicle = mock(Vehicle.class);
        InsuranceCoverage insurance = mock(InsuranceCoverage.class);
        MileagePlan plan = mock(MileagePlan.class);

        Reservation reservation = new Reservation(
                "HLD-123", user, vehicle, pendingReviewStatus, insurance, plan, null,
                Instant.now().plus(2, ChronoUnit.DAYS), Instant.now().plus(5, ChronoUnit.DAYS),
                BigDecimal.valueOf(100), BigDecimal.valueOf(300), null, null, true
        );

        reviewService.validateCanBePaid(reservation);
    }

    @Test
    void validateCanBePaid_WhenPendingPayment_DoesNotThrow() {
        Vehicle vehicle = mock(Vehicle.class);
        InsuranceCoverage insurance = mock(InsuranceCoverage.class);
        MileagePlan plan = mock(MileagePlan.class);

        Reservation reservation = new Reservation(
                "HLD-123", user, vehicle, pendingPaymentStatus, insurance, plan, null,
                Instant.now().plus(2, ChronoUnit.DAYS), Instant.now().plus(5, ChronoUnit.DAYS),
                BigDecimal.valueOf(100), BigDecimal.valueOf(300), null, null, true
        );

        reviewService.validateCanBePaid(reservation);
    }

    @Test
    void onDocumentsReviewed_WhenDocumentsApproved_TransitionsReservationAndResetsCashExpiration() {
        UserDocument licenseDoc = new UserDocument(user, licenseType, approvedStatus, "DL12345", "front.jpg", "back.jpg");
        UserDocument ccDoc = new UserDocument(user, ccType, approvedStatus, "10203040", "front.jpg", "back.jpg");
        when(userDocumentRepository.findByUserId(user.getId())).thenReturn(List.of(licenseDoc, ccDoc));

        Vehicle vehicle = mock(Vehicle.class);
        InsuranceCoverage insurance = mock(InsuranceCoverage.class);
        MileagePlan plan = mock(MileagePlan.class);
        Branch branch = mock(Branch.class);

        Instant pickupDate = Instant.now().plus(5, ChronoUnit.DAYS);
        Reservation reservation = new Reservation(
                "HLD-999", user, vehicle, pendingReviewStatus, insurance, plan, branch,
                pickupDate, pickupDate.plus(3, ChronoUnit.DAYS),
                BigDecimal.valueOf(150), BigDecimal.valueOf(450), "CASH-12345", null, true
        );
        reservation.setId(UUID.randomUUID());

        when(reservationRepository.findByCustomerOrderByCreatedAtDesc(user)).thenReturn(List.of(reservation));
        when(reservationStatusRepository.findByCodeIgnoreCase("PENDING_PAYMENT")).thenReturn(Optional.of(pendingPaymentStatus));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reviewService.onDocumentsReviewed(user);

        assertThat(reservation.getStatus().getCode()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservation.getCashPaymentExpiresAt()).isNotNull();
        // Since pickupDate is 5 days away, 72h is sooner, so cashPaymentExpiresAt is ~72h
        assertThat(reservation.getCashPaymentExpiresAt()).isBefore(pickupDate);

        verify(reservationRepository).save(reservation);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendWithMetadata(
                eq(user),
                eq("IN_APP"),
                eq("RESERVATION"),
                eq("¡Documentos validados exitosamente!"),
                anyString(),
                eq(reservation.getId()),
                metadataCaptor.capture()
        );

        Map<String, Object> captured = metadataCaptor.getValue();
        assertThat(captured.get("actionLabel")).isEqualTo("Continuar con mi reserva");
        assertThat(captured.get("actionPath")).isEqualTo("/reservas?detalle=" + reservation.getId());
        assertThat(captured.get("actionUrl")).isEqualTo("http://localhost:5173/reservas?detalle=" + reservation.getId());

        verify(notificationService).sendWithMetadata(
                eq(user),
                eq("EMAIL"),
                eq("RESERVATION"),
                eq("¡Documentos validados exitosamente!"),
                anyString(),
                eq(reservation.getId()),
                any()
        );
    }
}
