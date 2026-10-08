package com.drivique.api.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.drivique.api.dto.ReviewEligibilityResponseDTO;
import com.drivique.api.dto.ReviewRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.ReviewService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTests {

    @Mock ReservationRepository reservations;
    @Mock UserRepository users;
    @Mock BranchRepository branches;
    @Mock VehicleRepository vehicles;
    @Mock VehicleRatingRepository ratings;
    @Mock BranchReviewRepository branchReviews;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reservations, users, branches, vehicles, ratings, branchReviews);
    }

    @Test
    void publicVehicleReviewsRejectUnknownVehicle() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicles.existsById(vehicleId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.vehicleReviews(vehicleId));
        verifyNoInteractions(ratings);
    }

    @Test
    void completedOwnedReservationIsEligibleUntilReviewExists() {
        Fixture fixture = completedFixture();
        when(ratings.findByReservationId(fixture.reservationId)).thenReturn(Optional.empty());
        when(branchReviews.findByBranchIdOrderByCreatedAtDesc(fixture.branchId)).thenReturn(List.of());

        ReviewEligibilityResponseDTO result = service.reviewEligibility(fixture.reservationId, fixture.email);

        assertTrue(result.canReviewVehicle());
        assertTrue(result.canReviewBranch());
        assertNull(result.vehicleReview());
    }

    @Test
    void existingReviewIsReturnedAndPreventsDuplicateReview() {
        Fixture fixture = completedFixture();
        VehicleRating existing = mock(VehicleRating.class);
        UUID reviewId = UUID.randomUUID();
        when(existing.getId()).thenReturn(reviewId);
        when(existing.getUser()).thenReturn(fixture.user);
        when(existing.getRating()).thenReturn((short) 5);
        when(existing.getComment()).thenReturn("Excelente");
        when(existing.getCreatedAt()).thenReturn(Instant.parse("2026-10-08T12:00:00Z"));
        when(ratings.findByReservationId(fixture.reservationId)).thenReturn(Optional.of(existing));
        when(branchReviews.findByBranchIdOrderByCreatedAtDesc(fixture.branchId)).thenReturn(List.of());

        ReviewEligibilityResponseDTO result = service.reviewEligibility(fixture.reservationId, fixture.email);

        assertFalse(result.canReviewVehicle());
        assertNotNull(result.vehicleReview());
        assertEquals(reviewId, result.vehicleReview().id());
        assertEquals(5, result.vehicleReview().rating());
    }

    @Test
    void duplicateVehicleReviewIsRejected() {
        Fixture fixture = completedFixture();
        when(ratings.existsByReservationId(fixture.reservationId)).thenReturn(true);

        ReviewRequestDTO request = new ReviewRequestDTO(fixture.reservationId, (short) 4, "Buen vehículo");

        assertThrows(ConflictException.class, () -> service.rateVehicle(request, fixture.email));
        verify(ratings, never()).saveAndFlush(any());
    }

    @Test
    void anotherCustomerCannotReviewReservation() {
        Fixture fixture = completedFixture();
        User another = mock(User.class);
        when(another.getId()).thenReturn(UUID.randomUUID());
        when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("other@drivique.com")).thenReturn(Optional.of(another));

        ReviewRequestDTO request = new ReviewRequestDTO(fixture.reservationId, (short) 5, "No permitido");

        assertThrows(ResourceNotFoundException.class, () -> service.rateVehicle(request, "other@drivique.com"));
        verify(ratings, never()).saveAndFlush(any());
    }

    private Fixture completedFixture() {
        UUID reservationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        String email = "customer@drivique.com";

        User user = mock(User.class);
        when(user.getId()).thenReturn(customerId);
        when(user.getFullName()).thenReturn("Cliente Drivique");

        Reservation reservation = mock(Reservation.class);
        ReservationStatus status = mock(ReservationStatus.class);
        Vehicle vehicle = mock(Vehicle.class);
        Branch branch = mock(Branch.class);
        when(reservation.getId()).thenReturn(reservationId);
        when(reservation.getCustomer()).thenReturn(user);
        when(reservation.getStatus()).thenReturn(status);
        when(status.getCode()).thenReturn("COMPLETED");
        when(reservation.getVehicle()).thenReturn(vehicle);
        when(vehicle.isActive()).thenReturn(true);
        when(vehicle.getCurrentBranch()).thenReturn(branch);
        when(branch.getId()).thenReturn(branchId);
        when(reservation.getDeliveryPoints()).thenReturn(List.of());
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));

        return new Fixture(reservationId, branchId, email, user);
    }

    private record Fixture(UUID reservationId, UUID branchId, String email, User user) {}
}
