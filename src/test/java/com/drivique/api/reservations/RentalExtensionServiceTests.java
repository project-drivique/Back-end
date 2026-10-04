package com.drivique.api.reservations;

import com.drivique.api.service.*;

import com.drivique.api.dto.CreateExtensionRequestDTO;
import com.drivique.api.dto.RentalExtensionResponseDTO;
import com.drivique.api.dto.ReviewExtensionRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.model.*;
import com.drivique.api.repository.RentalExtensionRequestRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RentalExtensionServiceTests {

    private final RentalExtensionRequestRepository extensionRepository = mock(RentalExtensionRequestRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private RentalExtensionService service;

    private final String customerEmail = "customer@drivique.com";
    private final String reviewerEmail = "admin@drivique.com";
    private final UUID reservationId = UUID.randomUUID();
    private final UUID vehicleId = UUID.randomUUID();
    private final UUID extensionId = UUID.randomUUID();

    private User customer;
    private User reviewer;
    private Vehicle vehicle;
    private InsuranceCoverage insurance;
    private MileagePlan mileagePlan;
    private Reservation reservation;
    private ReservationStatus activeStatus;

    @BeforeEach
    void setUp() {
        service = new RentalExtensionService(extensionRepository, reservationRepository, userRepository);

        customer = mock(User.class);
        when(customer.getId()).thenReturn(UUID.randomUUID());
        when(customer.getEmail()).thenReturn(customerEmail);
        when(customer.getFirstName()).thenReturn("Carlos");
        when(customer.getLastName()).thenReturn("Gomez");
        when(customer.getRoles()).thenReturn(Collections.emptySet());

        reviewer = mock(User.class);
        when(reviewer.getId()).thenReturn(UUID.randomUUID());
        when(reviewer.getEmail()).thenReturn(reviewerEmail);
        when(reviewer.getFirstName()).thenReturn("Admin");
        when(reviewer.getLastName()).thenReturn("User");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(customerEmail)).thenReturn(Optional.of(customer));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(reviewerEmail)).thenReturn(Optional.of(reviewer));

        vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(vehicleId);
        when(vehicle.getDailyRate()).thenReturn(new BigDecimal("200000"));

        insurance = mock(InsuranceCoverage.class);
        when(insurance.getDailyRate()).thenReturn(new BigDecimal("30000"));

        mileagePlan = mock(MileagePlan.class);
        when(mileagePlan.getDailyRate()).thenReturn(new BigDecimal("10000"));

        activeStatus = new ReservationStatus("CONFIRMED", "Confirmed", true);

        Instant now = Instant.now();
        reservation = new Reservation(
                "RES-2026-0001",
                customer,
                vehicle,
                activeStatus,
                insurance,
                mileagePlan,
                null,
                now,
                now.plus(3, ChronoUnit.DAYS),
                new BigDecimal("200000"),
                new BigDecimal("720000"),
                null,
                null,
                true
        );

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
    }

    @Test
    void requestExtension_Success_CalculatesQuoteAndSavesPendingExtension() {
        Instant newReturnDate = reservation.getReturnDate().plus(2, ChronoUnit.DAYS);
        CreateExtensionRequestDTO request = new CreateExtensionRequestDTO(newReturnDate);

        when(reservationRepository.existsOverlappingReservationExcluding(
                eq(vehicleId), eq(reservation.getId()), eq(reservation.getReturnDate()), eq(newReturnDate)))
                .thenReturn(false);

        when(extensionRepository.existsByReservationIdAndStatusIgnoreCase(reservation.getId(), "PENDING"))
                .thenReturn(false);

        when(extensionRepository.save(any(RentalExtensionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        RentalExtensionResponseDTO response = service.requestExtension(reservationId, request, customerEmail);

        assertThat(response).isNotNull();
        assertThat(response.requestedReturnDate()).isEqualTo(newReturnDate);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.additionalDays()).isEqualTo(2);
        // Daily rate sum: 200000 (vehicle) + 30000 (insurance) + 10000 (mileage) = 240000 * 2 = 480000
        assertThat(response.additionalAmount()).isEqualByComparingTo("480000");

        verify(extensionRepository).save(any(RentalExtensionRequest.class));
    }

    @Test
    void requestExtension_ThrowsConflict_WhenVehicleHasOverlappingReservation() {
        Instant newReturnDate = reservation.getReturnDate().plus(2, ChronoUnit.DAYS);
        CreateExtensionRequestDTO request = new CreateExtensionRequestDTO(newReturnDate);

        when(reservationRepository.existsOverlappingReservationExcluding(
                eq(vehicleId), eq(reservation.getId()), eq(reservation.getReturnDate()), eq(newReturnDate)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requestExtension(reservationId, request, customerEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no se encuentra disponible para extender el alquiler");

        verify(extensionRepository, never()).save(any());
    }

    @Test
    void requestExtension_ThrowsConflict_WhenPendingExtensionAlreadyExists() {
        Instant newReturnDate = reservation.getReturnDate().plus(2, ChronoUnit.DAYS);
        CreateExtensionRequestDTO request = new CreateExtensionRequestDTO(newReturnDate);

        when(reservationRepository.existsOverlappingReservationExcluding(
                eq(vehicleId), eq(reservation.getId()), eq(reservation.getReturnDate()), eq(newReturnDate)))
                .thenReturn(false);

        when(extensionRepository.existsByReservationIdAndStatusIgnoreCase(reservation.getId(), "PENDING"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requestExtension(reservationId, request, customerEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe una solicitud de extensión pendiente");

        verify(extensionRepository, never()).save(any());
    }

    @Test
    void requestExtension_ThrowsIllegalArgument_WhenRequestedDateNotAfterCurrentReturnDate() {
        Instant invalidDate = reservation.getReturnDate().minus(1, ChronoUnit.HOURS);
        CreateExtensionRequestDTO request = new CreateExtensionRequestDTO(invalidDate);

        assertThatThrownBy(() -> service.requestExtension(reservationId, request, customerEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debe ser posterior a la fecha actual de devolución");
    }

    @Test
    void reviewExtension_Approved_UpdatesReservationReturnDateAndAmount() {
        Instant newReturnDate = reservation.getReturnDate().plus(2, ChronoUnit.DAYS);
        BigDecimal additionalAmount = new BigDecimal("480000");

        RentalExtensionRequest ext = new RentalExtensionRequest(reservation, newReturnDate, additionalAmount);
        when(extensionRepository.findById(extensionId)).thenReturn(Optional.of(ext));
        when(extensionRepository.save(any(RentalExtensionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        when(reservationRepository.existsOverlappingReservationExcluding(
                eq(vehicleId), eq(reservation.getId()), eq(reservation.getReturnDate()), eq(newReturnDate)))
                .thenReturn(false);

        ReviewExtensionRequestDTO reviewDTO = new ReviewExtensionRequestDTO("APPROVED", "Aprobado por agente");

        RentalExtensionResponseDTO response = service.reviewExtension(extensionId, reviewDTO, reviewerEmail);

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(ext.getStatus()).isEqualTo("APPROVED");
        assertThat(ext.getReviewedBy()).isEqualTo(reviewer);
        assertThat(reservation.getReturnDate()).isEqualTo(newReturnDate);
        assertThat(reservation.getTotalEstimated()).isEqualByComparingTo("1200000"); // 720000 + 480000

        verify(reservationRepository).save(reservation);
        verify(extensionRepository).save(ext);
    }

    @Test
    void reviewExtension_Rejected_DoesNotModifyReservationReturnDate() {
        Instant originalReturnDate = reservation.getReturnDate();
        Instant newReturnDate = originalReturnDate.plus(2, ChronoUnit.DAYS);
        BigDecimal additionalAmount = new BigDecimal("480000");

        RentalExtensionRequest ext = new RentalExtensionRequest(reservation, newReturnDate, additionalAmount);
        when(extensionRepository.findById(extensionId)).thenReturn(Optional.of(ext));
        when(extensionRepository.save(any(RentalExtensionRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewExtensionRequestDTO reviewDTO = new ReviewExtensionRequestDTO("REJECTED", "No hay disponibilidad");

        RentalExtensionResponseDTO response = service.reviewExtension(extensionId, reviewDTO, reviewerEmail);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(ext.getStatus()).isEqualTo("REJECTED");
        assertThat(ext.getReviewedBy()).isEqualTo(reviewer);
        assertThat(reservation.getReturnDate()).isEqualTo(originalReturnDate);

        verify(reservationRepository, never()).save(any());
        verify(extensionRepository).save(ext);
    }

    @Test
    void reviewExtension_ThrowsConflict_WhenAlreadyReviewed() {
        RentalExtensionRequest ext = new RentalExtensionRequest(reservation, reservation.getReturnDate().plus(1, ChronoUnit.DAYS), new BigDecimal("240000"));
        ext.setStatus("APPROVED");

        when(extensionRepository.findById(extensionId)).thenReturn(Optional.of(ext));

        ReviewExtensionRequestDTO reviewDTO = new ReviewExtensionRequestDTO("APPROVED", "Reintento");

        assertThatThrownBy(() -> service.reviewExtension(extensionId, reviewDTO, reviewerEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya fue procesada anteriormente");
    }

    @Test
    void getExtensionsByReservation_ReturnsList() {
        RentalExtensionRequest ext = new RentalExtensionRequest(reservation, reservation.getReturnDate().plus(1, ChronoUnit.DAYS), new BigDecimal("240000"));
        when(extensionRepository.findByReservationOrderByCreatedAtDesc(reservation)).thenReturn(List.of(ext));

        List<RentalExtensionResponseDTO> list = service.getExtensionsByReservation(reservationId, customerEmail);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).reservationCode()).isEqualTo("RES-2026-0001");
    }
}
