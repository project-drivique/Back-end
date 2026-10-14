package com.drivique.api.notifications;

import com.drivique.api.model.*;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.ReservationStatusRepository;
import com.drivique.api.service.NotificationService;
import com.drivique.api.service.ReservationDocumentReviewService;
import com.drivique.api.service.notification.BranchScheduleNotificationScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BranchScheduleNotificationSchedulerTests {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationStatusRepository reservationStatusRepository;

    @Mock
    private ReservationDocumentReviewService reservationDocumentReviewService;

    @Mock
    private NotificationService notificationService;

    private BranchScheduleNotificationScheduler scheduler;

    private Branch branch;
    private User customer;
    private ReservationStatus statusPendingSchedule;
    private ReservationStatus statusPendingPayment;
    private ReservationStatus statusPendingKyc;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        scheduler = new BranchScheduleNotificationScheduler(
                branchRepository,
                reservationRepository,
                reservationStatusRepository,
                reservationDocumentReviewService,
                notificationService,
                "http://localhost:5173"
        );

        branchId = UUID.randomUUID();
        branch = mock(Branch.class);
        when(branch.getId()).thenReturn(branchId);
        when(branch.getName()).thenReturn("Sucursal Centro");
        when(branch.getOpeningTime()).thenReturn(LocalTime.of(8, 0));
        when(branch.getClosingTime()).thenReturn(LocalTime.of(18, 0));

        customer = new User("Laura", "Admin", "cliente@drivique.com", "Pass123!");
        customer.setId(UUID.randomUUID());

        statusPendingSchedule = new ReservationStatus("PENDING_SCHEDULE", "Pending Schedule", true);
        statusPendingPayment = new ReservationStatus("PENDING_PAYMENT", "Pending Payment", true);
        statusPendingKyc = new ReservationStatus("PENDING_DOCUMENT_REVIEW", "Pending Review", true);

        when(reservationStatusRepository.findByCodeIgnoreCase("PENDING_PAYMENT"))
                .thenReturn(Optional.of(statusPendingPayment));
        when(reservationStatusRepository.findByCodeIgnoreCase("PENDING_DOCUMENT_REVIEW"))
                .thenReturn(Optional.of(statusPendingKyc));
    }

    @Test
    @DisplayName("isBranchCurrentlyOpen correctly evaluates operating hours")
    void isBranchCurrentlyOpen_evaluatesCorrectly() {
        // Standard hours 08:00 - 18:00
        assertThat(scheduler.isBranchCurrentlyOpen(branch, LocalTime.of(8, 0))).isTrue();
        assertThat(scheduler.isBranchCurrentlyOpen(branch, LocalTime.of(12, 30))).isTrue();
        assertThat(scheduler.isBranchCurrentlyOpen(branch, LocalTime.of(18, 0))).isTrue();
        assertThat(scheduler.isBranchCurrentlyOpen(branch, LocalTime.of(7, 59))).isFalse();
        assertThat(scheduler.isBranchCurrentlyOpen(branch, LocalTime.of(18, 1))).isFalse();

        // 24 Hours branch
        Branch b24 = mock(Branch.class);
        when(b24.getOpeningTime()).thenReturn(LocalTime.MIN);
        when(b24.getClosingTime()).thenReturn(LocalTime.MAX);
        assertThat(scheduler.isBranchCurrentlyOpen(b24, LocalTime.of(3, 15))).isTrue();

        // Null hours fallback
        Branch bNull = mock(Branch.class);
        when(bNull.getOpeningTime()).thenReturn(null);
        assertThat(scheduler.isBranchCurrentlyOpen(bNull, LocalTime.of(3, 15))).isTrue();

        // Overnight hours: 22:00 - 06:00
        Branch bOvernight = mock(Branch.class);
        when(bOvernight.getOpeningTime()).thenReturn(LocalTime.of(22, 0));
        when(bOvernight.getClosingTime()).thenReturn(LocalTime.of(6, 0));
        assertThat(scheduler.isBranchCurrentlyOpen(bOvernight, LocalTime.of(23, 0))).isTrue();
        assertThat(scheduler.isBranchCurrentlyOpen(bOvernight, LocalTime.of(4, 0))).isTrue();
        assertThat(scheduler.isBranchCurrentlyOpen(bOvernight, LocalTime.of(14, 0))).isFalse();
    }

    @Test
    @DisplayName("processBranchReservations transitions PENDING_SCHEDULE to PENDING_PAYMENT when KYC is approved")
    void processBranchReservations_kycApproved_transitionsToPendingPayment() {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getCurrentBranch()).thenReturn(branch);

        Instant pickupDate = Instant.now().plus(5, ChronoUnit.DAYS);
        Reservation res = new Reservation(
                "DRV-12345", customer, vehicle, statusPendingSchedule, null, null, branch,
                pickupDate, pickupDate.plus(2, ChronoUnit.DAYS),
                BigDecimal.valueOf(100), BigDecimal.valueOf(200), "CASH-999", null, true
        );
        res.setId(UUID.randomUUID());

        when(reservationRepository.findAll()).thenReturn(List.of(res));
        when(reservationDocumentReviewService.hasApprovedRequiredDocuments(customer.getId())).thenReturn(true);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = scheduler.processBranchReservations(branch);

        assertThat(processed).isEqualTo(1);
        assertThat(res.getStatus().getCode()).isEqualTo("PENDING_PAYMENT");
        assertThat(res.getCashPaymentExpiresAt()).isNotNull();

        // Verifies notifications sent (IN_APP, EMAIL, PUSH) with CTA metadata
        verify(notificationService).sendWithMetadata(
                eq(customer),
                eq("IN_APP"),
                eq("RESERVATION"),
                contains("Ya puedes seguir"),
                contains("ya abrió sus puertas"),
                eq(res.getId()),
                argThat(meta -> "Continuar con mi reserva".equals(meta.get("actionLabel")))
        );

        verify(notificationService).sendWithMetadata(
                eq(customer),
                eq("EMAIL"),
                eq("RESERVATION"),
                contains("Ya puedes seguir"),
                contains("ya abrió sus puertas"),
                eq(res.getId()),
                argThat(meta -> meta.containsKey("actionUrl"))
        );

        verify(notificationService).sendWithMetadata(
                eq(customer),
                eq("PUSH"),
                eq("RESERVATION"),
                contains("abrió"),
                contains("abrió"),
                eq(res.getId()),
                anyMap()
        );
    }

    @Test
    @DisplayName("processBranchReservations transitions PENDING_SCHEDULE to PENDING_DOCUMENT_REVIEW when KYC is pending")
    void processBranchReservations_kycPending_transitionsToPendingReview() {
        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getCurrentBranch()).thenReturn(branch);

        Instant pickupDate = Instant.now().plus(5, ChronoUnit.DAYS);
        Reservation res = new Reservation(
                "DRV-67890", customer, vehicle, statusPendingSchedule, null, null, null,
                pickupDate, pickupDate.plus(2, ChronoUnit.DAYS),
                BigDecimal.valueOf(100), BigDecimal.valueOf(200), null, null, true
        );
        res.setId(UUID.randomUUID());

        when(reservationRepository.findAll()).thenReturn(List.of(res));
        when(reservationDocumentReviewService.hasApprovedRequiredDocuments(customer.getId())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int processed = scheduler.processBranchReservations(branch);

        assertThat(processed).isEqualTo(1);
        assertThat(res.getStatus().getCode()).isEqualTo("PENDING_DOCUMENT_REVIEW");

        verify(notificationService).sendWithMetadata(
                eq(customer),
                eq("IN_APP"),
                eq("RESERVATION"),
                anyString(),
                contains("estará validando tus documentos"),
                eq(res.getId()),
                anyMap()
        );
    }

    @Test
    @DisplayName("processBranchReservations ignores reservations from other branches or statuses")
    void processBranchReservations_ignoresUnrelated() {
        Branch otherBranch = mock(Branch.class);
        when(otherBranch.getId()).thenReturn(UUID.randomUUID());

        Vehicle otherVehicle = mock(Vehicle.class);
        when(otherVehicle.getCurrentBranch()).thenReturn(otherBranch);

        Instant pickupDate = Instant.now().plus(5, ChronoUnit.DAYS);
        Reservation resOtherBranch = new Reservation(
                "DRV-OTHER", customer, otherVehicle, statusPendingSchedule, null, null, null,
                pickupDate, pickupDate.plus(2, ChronoUnit.DAYS),
                BigDecimal.valueOf(100), BigDecimal.valueOf(200), null, null, true
        );

        Reservation resOtherStatus = new Reservation(
                "DRV-PAID", customer, otherVehicle, statusPendingPayment, null, null, null,
                pickupDate, pickupDate.plus(2, ChronoUnit.DAYS),
                BigDecimal.valueOf(100), BigDecimal.valueOf(200), null, null, true
        );

        when(reservationRepository.findAll()).thenReturn(List.of(resOtherBranch, resOtherStatus));

        int processed = scheduler.processBranchReservations(branch);

        assertThat(processed).isZero();
        verify(reservationRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("processOpenBranchesReservations checks open branches and invokes processing")
    void processOpenBranchesReservations_runsForOpenBranches() {
        when(branchRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(branch));
        when(branch.getOpeningTime()).thenReturn(LocalTime.MIN);
        when(branch.getClosingTime()).thenReturn(LocalTime.MAX);
        when(reservationRepository.findAll()).thenReturn(Collections.emptyList());

        int count = scheduler.processOpenBranchesReservations();

        assertThat(count).isZero();
        verify(branchRepository).findByActiveTrueOrderByNameAsc();
    }
}
