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

class ReservationServiceQueryTests extends ReservationServiceTestBase {

    @Test
    void getReservationById_Success() {
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

        ReservationResponseDTO response = service.getReservationById(reservationId, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("RES-2026-0001");
    }

    @Test
    void getReservationById_ThrowsNotFound_WhenIdDoesNotExist() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReservationById(reservationId, userEmail))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reserva no encontrada");
    }

    @Test
    void getReservationByCode_Success() {
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
        when(reservationRepository.findByCodeIgnoreCase("RES-2026-0001")).thenReturn(Optional.of(r));

        ReservationResponseDTO response = service.getReservationByCode("RES-2026-0001", userEmail);

        assertThat(response.code()).isEqualTo("RES-2026-0001");
    }

    @Test
    void cancelReservation_Success() {
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
        ReservationStatus cancelledStatus = new ReservationStatus("CANCELLED_BY_USER", "Cancelled by user", false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(reservationStatusRepository.findByCodeIgnoreCase("CANCELLED_BY_USER")).thenReturn(Optional.of(cancelledStatus));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(r);

        CancelReservationRequestDTO cancelReq = new CancelReservationRequestDTO("User requested cancellation");
        ReservationResponseDTO resp = service.cancelReservation(reservationId, cancelReq, userEmail);

        assertThat(resp).isNotNull();
        assertThat(r.getStatus().getCode()).isEqualTo("CANCELLED_BY_USER");
    }

    @Test
    void cancelReservation_ThrowsConflict_WhenAlreadyFinalState() {
        ReservationStatus completedStatus = new ReservationStatus("COMPLETED", "Completed", false);
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                completedStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                false
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        CancelReservationRequestDTO cancelReq = new CancelReservationRequestDTO("Already done");
        assertThatThrownBy(() -> service.cancelReservation(reservationId, cancelReq, userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("estado final");
    }

    @Test
    void getAllowedTransitions_Success() {
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

        AllowedTransitionsResponseDTO result = service.getAllowedTransitions(reservationId, userEmail);

        assertThat(result).isNotNull();
        assertThat(result.allowedTransitions()).contains("CONFIRMED", "CANCELLED_BY_USER");
    }

    @Test
    void modifyReservationDates_Success() {
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

        ModifyReservationDatesRequestDTO modReq = new ModifyReservationDatesRequestDTO(
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS)
        );

        ReservationResponseDTO response = service.modifyReservationDates(reservationId, modReq, userEmail);
        assertThat(response).isNotNull();
    }

    @Test
    void getReservationById_Success_ForOwner() {
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

        ReservationResponseDTO response = service.getReservationById(reservationId, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("RES-2026-0001");
    }

    @Test
    void getReservationById_Success_ForStaff() {
        User staffUser = mock(User.class);
        when(staffUser.getId()).thenReturn(UUID.randomUUID());
        Role staffRole = mock(Role.class);
        when(staffRole.getCode()).thenReturn("AGENT");
        when(staffUser.getRoles()).thenReturn(Set.of(staffRole));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("agent@drivique.com")).thenReturn(Optional.of(staffUser));

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

        ReservationResponseDTO response = service.getReservationById(reservationId, "agent@drivique.com");

        assertThat(response).isNotNull();
    }

    @Test
    void getReservationById_ThrowsNotFound_WhenUnrelatedUser() {
        User intruder = mock(User.class);
        when(intruder.getId()).thenReturn(UUID.randomUUID());
        when(intruder.getRoles()).thenReturn(Collections.emptySet());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("intruder@drivique.com")).thenReturn(Optional.of(intruder));

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

        assertThatThrownBy(() -> service.getReservationById(reservationId, "intruder@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reserva no encontrada");
    }

    @Test
    void getMyReservations_ReturnsList() {
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
        when(reservationRepository.findByCustomerOrderByCreatedAtDesc(user)).thenReturn(List.of(r));

        List<ReservationResponseDTO> list = service.getMyReservations(userEmail);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).code()).isEqualTo("RES-2026-0001");
    }
}
