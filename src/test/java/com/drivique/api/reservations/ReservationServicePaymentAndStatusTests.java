package com.drivique.api.reservations;

import com.drivique.api.dto.*;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.NotificationService;
import com.drivique.api.service.PromotionValidationService;
import com.drivique.api.service.ReservationDocumentReviewService;
import com.drivique.api.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReservationServicePaymentAndStatusTests extends ReservationServiceTestBase {

    @Test
    void confirmReservationPayment_Success_WhenHoldAndWompiApproves() {
        Reservation r = new Reservation(
                "HLD-2026-0001", user, vehicle, pendingStatus, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        stubPaymentCollaborators();
        when(reservationRepository.countByCodeStartingWith(any())).thenReturn(0L);
        when(reservationStatusRepository.findByCodeIgnoreCase("CONFIRMED"))
                .thenReturn(Optional.of(new ReservationStatus("CONFIRMED", "Confirmed", true)));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReservationResponseDTO response = service.confirmReservationPayment(reservationId.toString(), "tx-approved", userEmail);

        assertThat(response).isNotNull();
        assertThat(response.code()).startsWith("RES-");
        assertThat(response.status()).isEqualTo("CONFIRMED");
        verify(wompiPaymentService).verifyAndRecordApprovedPayment(eq("tx-approved"), any(), eq(userEmail));
    }

    @Test
    void confirmReservationPayment_WithoutTransactionId_IsRefusedAndKeepsStatus() {
        Reservation r = new Reservation(
                "HLD-2026-0002", user, vehicle, pendingStatus, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        stubPaymentCollaborators();
        ReservationStatus before = r.getStatus();

        assertThatThrownBy(() -> service.confirmReservationPayment(reservationId.toString(), null, userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("transacción de pago aprobada");

        assertThat(r.getStatus()).isSameAs(before);
        assertThat(r.getCode()).startsWith("HLD-");
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void confirmReservationPayment_WhenWompiRejects_KeepsReservationUnpaid() {
        Reservation r = new Reservation(
                "HLD-2026-0003", user, vehicle, pendingStatus, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        com.drivique.api.model.RentalContract contract = stubPaymentCollaborators();
        when(wompiPaymentService.verifyAndRecordApprovedPayment("tx-declined", contract, userEmail))
                .thenThrow(new ConflictException("La transacción en Wompi no se encuentra aprobada (estado: DECLINED)."));
        ReservationStatus before = r.getStatus();

        assertThatThrownBy(() -> service.confirmReservationPayment(reservationId.toString(), "tx-declined", userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no se encuentra aprobada");

        assertThat(r.getStatus()).isSameAs(before);
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void confirmReservationPayment_WhenAlreadyPaid_ReturnsWithoutVerifyingAgain() {
        ReservationStatus signature = new ReservationStatus("PENDING_SIGNATURE", "Pending signature", true);
        Reservation r = new Reservation(
                "RES-2026-0001", user, vehicle, signature, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        com.drivique.api.model.RentalContract contract = stubPaymentCollaborators();
        when(paymentRepository.existsByContractIdAndStatusCodeIgnoreCase(contract.getId(), "APPROVED")).thenReturn(true);

        ReservationResponseDTO response = service.confirmReservationPayment(reservationId.toString(), "tx-approved", userEmail);

        assertThat(response.code()).isEqualTo("RES-2026-0001");
        assertThat(response.status()).isEqualTo("PENDING_SIGNATURE");
        verify(wompiPaymentService, never()).verifyAndRecordApprovedPayment(any(), any(), any());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void createReservation_PicksFirstActiveInsuranceAndMileage_WhenNullProvided() {
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);

        when(reservationRepository.existsOverlappingReservation(eq(vehicleId), eq(pickup), eq(returnDate)))
                .thenReturn(false);
        when(insuranceCoverageRepository.findAll()).thenReturn(List.of(insurance));
        when(mileagePlanRepository.findAll()).thenReturn(List.of(mileagePlan));

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                null,
                null,
                null,
                null,
                branchId
        );

        ReservationResponseDTO response = service.createReservation(request, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.insuranceCoverageName()).isEqualTo("Cobertura Total");
        assertThat(response.mileagePlanName()).isEqualTo("Ilimitado");
    }

    @Test
    void modifyReservationServices_Success() {
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(r);

        ModifyReservationServicesRequestDTO req = new ModifyReservationServicesRequestDTO(List.of(additionalServiceId), insuranceId, mileagePlanId);
        ReservationResponseDTO response = service.modifyReservationServices(reservationId, req, userEmail);
        assertThat(response).isNotNull();
    }

    @Test
    void createReservation_WhenDocumentsNotApproved_SetsStatusPendingDocumentReviewWithPaymentWindow() {
        when(reservationDocumentReviewService.hasApprovedRequiredDocuments(user.getId())).thenReturn(false);

        ReservationStatus docReviewStatus = mock(ReservationStatus.class);
        when(docReviewStatus.getCode()).thenReturn("PENDING_DOCUMENT_REVIEW");
        when(docReviewStatus.getName()).thenReturn("Pending review");
        when(docReviewStatus.isBlocksAvailability()).thenReturn(true);
        when(reservationStatusRepository.findByCodeIgnoreCase("PENDING_DOCUMENT_REVIEW")).thenReturn(Optional.of(docReviewStatus));

        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);

        when(reservationRepository.existsOverlappingReservation(eq(vehicleId), eq(pickup), eq(returnDate))).thenReturn(false);

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId, pickup, returnDate, insuranceId, mileagePlanId, List.of(), null, branchId
        );

        ReservationResponseDTO response = service.createReservation(request, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("PENDING_DOCUMENT_REVIEW");
        assertThat(response.cashPaymentCode()).isNotNull();
        // toda reserva pendiente vence con el mismo plazo, para no bloquear fechas para siempre
        assertThat(response.cashPaymentExpiresAt()).isNotNull();

        verify(notificationService).send(
                eq(user),
                eq("EMAIL"),
                eq("RESERVATION_PENDING_DOCUMENT_REVIEW"),
                eq("Reserva registrada - Documentos en validación"),
                anyString(),
                any()
        );
        verify(notificationService).send(
                eq(user),
                eq("IN_APP"),
                eq("RESERVATION_PENDING_DOCUMENT_REVIEW"),
                eq("Reserva registrada - Documentos en validación"),
                anyString(),
                any()
        );
        // Verify RESERVATION domain event is NOT prematurely emitted
        verify(notificationService, never()).emitDomainEvent(any(), eq("RESERVATION"), any(), any(), any(), any());
    }

    @Test
    void confirmReservationPayment_WhenScheduleConflict_ThrowsConflictException() {
        ReservationStatus docReviewStatus = mock(ReservationStatus.class);
        when(docReviewStatus.getCode()).thenReturn("PENDING_SCHEDULE");

        Reservation r = new Reservation(
                "HLD-12345", user, vehicle, docReviewStatus, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        doThrow(new ConflictException("La reserva fue registrada fuera del horario de atención"))
                .when(reservationDocumentReviewService).validateCanBePaid(r);

        assertThatThrownBy(() -> service.confirmReservationPayment(reservationId, userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("horario de atención");
    }

    @Test
    void getAllowedTransitions_WhenPendingDocumentReview_ReturnsAllowedTransitions() {
        ReservationStatus docReviewStatus = mock(ReservationStatus.class);
        when(docReviewStatus.getCode()).thenReturn("PENDING_DOCUMENT_REVIEW");

        Reservation r = new Reservation(
                "HLD-12345", user, vehicle, docReviewStatus, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        AllowedTransitionsResponseDTO response = service.getAllowedTransitions(reservationId, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.currentStatus()).isEqualTo("PENDING_DOCUMENT_REVIEW");
        assertThat(response.allowedTransitions()).containsExactlyInAnyOrder("PENDING_PAYMENT", "CANCELLED_BY_USER");
    }

    @Test
    void reportPaymentFailed_EmitsDeclinedPaymentNotification() {
        Reservation r = new Reservation(
                "HLD-FAIL1", user, vehicle, pendingStatus, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        r.setId(reservationId);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(user));

        service.reportPaymentFailed(reservationId, userEmail);

        verify(notificationService).emitDomainEvent(
                eq(user),
                eq("PAYMENT"),
                eq("HIGH"),
                eq(reservationId),
                anyMap(),
                anyString()
        );
    }

    @Test
    void getAllowedTransitions_WhenPendingSchedule_ReturnsAllowedTransitions() {
        ReservationStatus scheduleStatus = mock(ReservationStatus.class);
        when(scheduleStatus.getCode()).thenReturn("PENDING_SCHEDULE");

        Reservation r = new Reservation(
                "HLD-SCHED", user, vehicle, scheduleStatus, insurance, mileagePlan, branch,
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        AllowedTransitionsResponseDTO response = service.getAllowedTransitions(reservationId, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.currentStatus()).isEqualTo("PENDING_SCHEDULE");
        assertThat(response.allowedTransitions()).containsExactlyInAnyOrder("PENDING_PAYMENT", "PENDING_DOCUMENT_REVIEW", "CANCELLED_BY_USER");
    }
}
