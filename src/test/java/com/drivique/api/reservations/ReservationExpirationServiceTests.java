package com.drivique.api.reservations;

import com.drivique.api.service.*;

import com.drivique.api.dto.ExpiredReservationsSummaryDTO;
import com.drivique.api.job.ReservationExpirationJob;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationStatus;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.ReservationStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReservationExpirationServiceTests {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationStatusRepository reservationStatusRepository = mock(ReservationStatusRepository.class);
    private final ReservationNotificationService notificationService = mock(ReservationNotificationService.class);

    private ReservationExpirationService service;
    private ReservationExpirationJob job;

    @BeforeEach
    void setUp() {
        service = new ReservationExpirationService(
                reservationRepository,
                reservationStatusRepository,
                notificationService
        );
        job = new ReservationExpirationJob(service);
    }

    @Test
    void expirePendingReservations_WhenNoExpiredReservations_ReturnsZeroSummary() {
        when(reservationRepository.findExpiredPendingReservations(eq("PENDING_PAYMENT"), any(Instant.class)))
                .thenReturn(List.of());

        ExpiredReservationsSummaryDTO result = service.expirePendingReservations();

        assertThat(result.expiredCount()).isZero();
        assertThat(result.expiredReservationCodes()).isEmpty();
        verify(reservationRepository, never()).save(any());
        verify(notificationService, never()).sendExpirationNotification(any());
    }

    @Test
    void expirePendingReservations_WhenExpiredReservationsFound_CancelsReservationsAndFreesAvailability() {
        ReservationStatus pendingStatus = new ReservationStatus("PENDING_PAYMENT", "Pending payment", true);
        ReservationStatus cancelledStatus = new ReservationStatus("CANCELLED_BY_TIMEOUT", "Cancelled by timeout", false);

        User customer = mock(User.class);
        when(customer.getEmail()).thenReturn("customer@drivique.com");

        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(UUID.randomUUID());
        when(vehicle.getModel()).thenReturn("Corolla Cross");

        Reservation reservation1 = mock(Reservation.class);
        when(reservation1.getCode()).thenReturn("RES-2026-0001");
        when(reservation1.getCustomer()).thenReturn(customer);
        when(reservation1.getVehicle()).thenReturn(vehicle);
        when(reservation1.getCashPaymentExpiresAt()).thenReturn(Instant.now().minusSeconds(3600));

        Reservation reservation2 = mock(Reservation.class);
        when(reservation2.getCode()).thenReturn("RES-2026-0002");
        when(reservation2.getCustomer()).thenReturn(customer);
        when(reservation2.getVehicle()).thenReturn(vehicle);
        when(reservation2.getCashPaymentExpiresAt()).thenReturn(Instant.now().minusSeconds(7200));

        when(reservationRepository.findExpiredPendingReservations(eq("PENDING_PAYMENT"), any(Instant.class)))
                .thenReturn(List.of(reservation1, reservation2));

        when(reservationStatusRepository.findByCodeIgnoreCase("CANCELLED_BY_TIMEOUT"))
                .thenReturn(Optional.of(cancelledStatus));

        ExpiredReservationsSummaryDTO result = service.expirePendingReservations();

        assertThat(result.expiredCount()).isEqualTo(2);
        assertThat(result.expiredReservationCodes()).containsExactly("RES-2026-0001", "RES-2026-0002");

        verify(reservation1).setStatus(cancelledStatus);
        verify(reservation1).setBlocksAvailability(false);
        verify(reservationRepository).save(reservation1);
        verify(notificationService).sendExpirationNotification(reservation1);

        verify(reservation2).setStatus(cancelledStatus);
        verify(reservation2).setBlocksAvailability(false);
        verify(reservationRepository).save(reservation2);
        verify(notificationService).sendExpirationNotification(reservation2);
    }

    @Test
    void expirePendingReservations_WhenCancelledStatusNotFoundInDb_CreatesStatusAndProceeds() {
        ReservationStatus newCancelledStatus = new ReservationStatus("CANCELLED_BY_TIMEOUT", "Cancelada por expiración de plazo de pago", false);

        User customer = mock(User.class);
        when(customer.getEmail()).thenReturn("customer@drivique.com");

        Vehicle vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(UUID.randomUUID());

        Reservation reservation = mock(Reservation.class);
        when(reservation.getCode()).thenReturn("RES-2026-0003");
        when(reservation.getCustomer()).thenReturn(customer);
        when(reservation.getVehicle()).thenReturn(vehicle);

        when(reservationRepository.findExpiredPendingReservations(eq("PENDING_PAYMENT"), any(Instant.class)))
                .thenReturn(List.of(reservation));

        when(reservationStatusRepository.findByCodeIgnoreCase("CANCELLED_BY_TIMEOUT"))
                .thenReturn(Optional.empty());
        when(reservationStatusRepository.save(any(ReservationStatus.class)))
                .thenReturn(newCancelledStatus);

        ExpiredReservationsSummaryDTO result = service.expirePendingReservations();

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.expiredReservationCodes()).containsExactly("RES-2026-0003");

        verify(reservationStatusRepository).save(any(ReservationStatus.class));
        verify(reservation).setStatus(newCancelledStatus);
        verify(reservation).setBlocksAvailability(false);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void job_ExecutesWithoutThrowingException() {
        when(reservationRepository.findExpiredPendingReservations(eq("PENDING_PAYMENT"), any(Instant.class)))
                .thenReturn(List.of());

        job.executeReservationExpiration();

        verify(reservationRepository).findExpiredPendingReservations(eq("PENDING_PAYMENT"), any(Instant.class));
    }
}
