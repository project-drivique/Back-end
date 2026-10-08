package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final ReservationRepository reservations;
    private final UserRepository users;
    private final BranchRepository branches;
    private final VehicleRepository vehicles;
    private final VehicleRatingRepository ratings;
    private final BranchReviewRepository branchReviews;

    public ReviewService(ReservationRepository reservations, UserRepository users, BranchRepository branches, VehicleRepository vehicles, VehicleRatingRepository ratings, BranchReviewRepository branchReviews) {
        this.reservations = reservations;
        this.users = users;
        this.branches = branches;
        this.vehicles = vehicles;
        this.ratings = ratings;
        this.branchReviews = branchReviews;
    }

    @Transactional
    public void rateVehicle(ReviewRequestDTO input, String email) {
        Reservation r = completedOwned(input.reservationId(), email);
        if (!r.getVehicle().isActive()) {
            throw new ResourceNotFoundException("Vehículo inactivo.");
        }
        if (ratings.existsByReservationId(r.getId()))
            throw new ConflictException("La reserva ya calificó el vehículo.");
        ratings.saveAndFlush(new VehicleRating(r, r.getVehicle(), current(email), input.rating(), text(input.comment())));
    }

    @Transactional
    public void reviewBranch(BranchReviewRequestDTO input, String email) {
        Reservation r = completedOwned(input.review().reservationId(), email);
        if (branchReviews.existsByReservationIdAndBranchId(r.getId(), input.branchId()))
            throw new ConflictException("La reserva ya calificó esta sede.");

        boolean isValidBranch = r.getVehicle().getCurrentBranch().getId().equals(input.branchId()) ||
                r.getDeliveryPoints().stream()
                        .map(ReservationDeliveryPoint::getBranch)
                        .filter(Objects::nonNull)
                        .anyMatch(b -> b.getId().equals(input.branchId()));

        if (!isValidBranch) {
            throw new ConflictException("La sede reseñada no pertenece a la reserva.");
        }

        Branch branch = branches.findById(input.branchId()).orElseThrow(() -> new ResourceNotFoundException("Sede no encontrada"));
        if (!branch.isActive()) {
            throw new ResourceNotFoundException("Sede inactiva.");
        }
        branchReviews.saveAndFlush(new BranchReview(branch, current(email), r, input.review().rating(), text(input.review().comment())));
    }

    @Transactional(readOnly = true)
    public BranchReviewsResponseDTO branchReviews(UUID branchId) {
        if (!branches.existsById(branchId)) {
            throw new ResourceNotFoundException("Sede no encontrada");
        }
        List<BranchReview> all = branchReviews.findByBranchIdOrderByCreatedAtDesc(branchId);
        List<BranchReviewResponseDTO> list = all.stream().map(b -> new BranchReviewResponseDTO(b.getId(), b.getUser().getFullName(), b.getRating(), b.getComment(), b.getCreatedAt())).toList();
        Double average = branchReviews.averageByBranchId(branchId);
        return new BranchReviewsResponseDTO(branchId, average == null ? 0 : average, list.size(), list);
    }

    @Transactional(readOnly = true)
    public VehicleReviewsResponseDTO vehicleReviews(UUID vehicleId) {
        if (!vehicles.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Vehículo no encontrado");
        }
        List<VehicleRating> all = ratings.findByVehicleIdOrderByCreatedAtDesc(vehicleId);
        List<VehicleReviewResponseDTO> list = all.stream().map(v -> new VehicleReviewResponseDTO(v.getId(), v.getUser().getFullName(), v.getRating(), v.getComment(), v.getCreatedAt())).toList();
        Double average = ratings.averageByVehicleId(vehicleId);
        return new VehicleReviewsResponseDTO(vehicleId, average == null ? 0 : average, list.size(), list);
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponseDTO reviewEligibility(UUID reservationId, String email) {
        Reservation r = reservations.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        User u = current(email);
        if (!r.getCustomer().getId().equals(u.getId()))
            throw new ResourceNotFoundException("Reserva no encontrada");
        boolean isCompleted = r.getStatus() != null && "COMPLETED".equalsIgnoreCase(r.getStatus().getCode());
        Optional<VehicleRating> existingVehicleReview = ratings.findByReservationId(r.getId());
        boolean canReviewVehicle = isCompleted && existingVehicleReview.isEmpty();

        Set<UUID> reservationBranchIds = new HashSet<>();
        if (r.getVehicle().getCurrentBranch() != null) {
            reservationBranchIds.add(r.getVehicle().getCurrentBranch().getId());
        }
        r.getDeliveryPoints().stream()
                .map(ReservationDeliveryPoint::getBranch)
                .filter(Objects::nonNull)
                .map(Branch::getId)
                .forEach(reservationBranchIds::add);
        boolean canReviewBranch = isCompleted && reservationBranchIds.stream().anyMatch(branchId ->
                branchReviews.findByBranchIdOrderByCreatedAtDesc(branchId).stream()
                        .noneMatch(review -> review.getReservation().getId().equals(r.getId())));

        VehicleReviewResponseDTO existingReview = existingVehicleReview
                .map(v -> new VehicleReviewResponseDTO(v.getId(), v.getUser().getFullName(), v.getRating(), v.getComment(), v.getCreatedAt()))
                .orElse(null);
        return new ReviewEligibilityResponseDTO(canReviewVehicle, canReviewBranch, existingReview);
    }

    private Reservation completedOwned(UUID id, String email) {
        Reservation r = reservations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        User u = current(email);
        if (!r.getCustomer().getId().equals(u.getId()))
            throw new ResourceNotFoundException("Reserva no encontrada");
        if (r.getStatus() == null || !"COMPLETED".equalsIgnoreCase(r.getStatus().getCode()))
            throw new ConflictException("Solo se puede calificar una reserva completada.");
        return r;
    }

    private User current(String e) {
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(e).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private String text(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
