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

class ReservationServiceTests extends ReservationServiceTestBase {

    @Test
    void createsReservationSuccessfullyWithCalculationsAndLocks() {
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);

        when(reservationRepository.existsOverlappingReservation(eq(vehicleId), eq(pickup), eq(returnDate)))
                .thenReturn(false);

        when(promotionValidationService.validate(any(PromotionValidationRequestDTO.class), eq(userEmail)))
                .thenReturn(new PromotionValidationResponseDTO(promotionId, "DESC10", "PERCENTAGE", new BigDecimal("10.00"), new BigDecimal("66000.00"), new BigDecimal("660000.00")));

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(additionalServiceId),
                "DESC10",
                branchId
        );

        ReservationResponseDTO response = service.createReservation(request, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.code()).startsWith("HLD-");
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.rentalDays()).isEqualTo(3);
        assertThat(response.vehicleDailyRate()).isEqualByComparingTo("220000");
        assertThat(response.totalEstimated()).isEqualByComparingTo("1151370");

        verify(vehicleRepository).findByIdWithPessimisticLock(vehicleId);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void rejectsReservationWhenVehicleHasOverlappingActiveReservation() {
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);

        when(reservationRepository.existsOverlappingReservation(eq(vehicleId), eq(pickup), eq(returnDate)))
                .thenReturn(true);

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createReservation(request, userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no se encuentra disponible");
    }

    @Test
    void repeatedIdenticalRequest_ReturnsTheOpenReservationInsteadOfFailingWithConflict() {
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);
        Reservation open = openRequestOfTheSameTrip(pickup, returnDate);
        // El primer intento ya bloquea el vehículo: sin idempotencia el segundo recibiría 409
        when(reservationRepository.existsOverlappingReservation(eq(vehicleId), eq(pickup), eq(returnDate))).thenReturn(true);

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId, pickup, returnDate, insuranceId, mileagePlanId, List.of(), null, branchId);

        ReservationResponseDTO response = service.createReservation(request, userEmail);

        assertThat(response.code()).isEqualTo(open.getCode());
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void requestWithDifferentServices_IsNotTreatedAsRepeated() {
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);
        openRequestOfTheSameTrip(pickup, returnDate);
        when(reservationRepository.existsOverlappingReservation(eq(vehicleId), eq(pickup), eq(returnDate))).thenReturn(true);

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId, pickup, returnDate, insuranceId, mileagePlanId, List.of(additionalServiceId), null, branchId);

        assertThatThrownBy(() -> service.createReservation(request, userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no se encuentra disponible");
    }

    @Test
    void rejectsReservationWhenReturnDatePrecedesPickupDate() {
        Instant pickup = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant returnDate = Instant.now().plus(2, ChronoUnit.DAYS);

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createReservation(request, userEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("devolución debe ser posterior");
    }

    @Test
    void rejectsReservationWhenPickupDateIsInPast() {
        Instant pickup = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant returnDate = Instant.now().plus(2, ChronoUnit.DAYS);

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createReservation(request, userEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser en el pasado");
    }

    @Test
    void rejectsReservationWhenVehicleDoesNotAllowReservation() {
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);

        when(vehicleStatus.allowsReservation()).thenReturn(false);

        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createReservation(request, userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no está habilitado");
    }
}
