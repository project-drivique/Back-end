package com.drivique.api.service;

import com.drivique.api.dto.CreateExtensionRequestDTO;
import com.drivique.api.dto.RentalExtensionResponseDTO;
import com.drivique.api.dto.ReviewExtensionRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.RentalExtensionRequest;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationAdditionalService;
import com.drivique.api.model.User;
import com.drivique.api.repository.RentalExtensionRequestRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RentalExtensionService {

    private final RentalExtensionRequestRepository extensionRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public RentalExtensionService(
            RentalExtensionRequestRepository extensionRepository,
            ReservationRepository reservationRepository,
            UserRepository userRepository
    ) {
        this.extensionRepository = extensionRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RentalExtensionResponseDTO requestExtension(UUID reservationId, CreateExtensionRequestDTO request, String userEmail) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        validateAccess(reservation, user);

        if (reservation.getStatus() != null) {
            String statusCode = reservation.getStatus().getCode();
            if ("CANCELLED".equalsIgnoreCase(statusCode) || "CANCELLED_BY_TIMEOUT".equalsIgnoreCase(statusCode) || "COMPLETED".equalsIgnoreCase(statusCode)) {
                throw new ConflictException("La reserva no se encuentra en un estado que permita solicitar extensiones (Estado actual: " + statusCode + ").");
            }
        }

        if (request.requestedReturnDate() == null) {
            throw new IllegalArgumentException("La fecha solicitada de devolución es obligatoria");
        }

        if (!request.requestedReturnDate().isAfter(reservation.getReturnDate())) {
            throw new IllegalArgumentException("La nueva fecha de devolución debe ser posterior a la fecha actual de devolución");
        }

        boolean hasOverlap = reservationRepository.existsOverlappingReservationExcluding(
                reservation.getVehicle().getId(),
                reservation.getId(),
                reservation.getReturnDate(),
                request.requestedReturnDate()
        );
        if (hasOverlap) {
            throw new ConflictException("El vehículo no se encuentra disponible para extender el alquiler en las fechas solicitadas debido a otra reserva programada.");
        }

        if (extensionRepository.existsByReservationIdAndStatusIgnoreCase(reservation.getId(), "PENDING")) {
            throw new ConflictException("Ya existe una solicitud de extensión pendiente de aprobación para esta reserva.");
        }

        long extraSeconds = Duration.between(reservation.getReturnDate(), request.requestedReturnDate()).toSeconds();
        int extraDays = (int) Math.max(1, (extraSeconds + 86399) / 86400);

        BigDecimal vehicleDailyRate = reservation.getVehicle() != null ? reservation.getVehicle().getDailyRate() : BigDecimal.ZERO;
        BigDecimal insuranceDailyRate = reservation.getInsuranceCoverage() != null ? reservation.getInsuranceCoverage().getDailyRate() : BigDecimal.ZERO;
        BigDecimal mileageDailyRate = reservation.getMileagePlan() != null ? reservation.getMileagePlan().getDailyRate() : BigDecimal.ZERO;

        BigDecimal servicesDailyRate = BigDecimal.ZERO;
        if (reservation.getAdditionalServices() != null) {
            for (ReservationAdditionalService service : reservation.getAdditionalServices()) {
                BigDecimal sub = service.getDailyRate().multiply(BigDecimal.valueOf(service.getQuantity()));
                servicesDailyRate = servicesDailyRate.add(sub);
            }
        }

        BigDecimal dailyRateTotal = vehicleDailyRate.add(insuranceDailyRate).add(mileageDailyRate).add(servicesDailyRate);
        BigDecimal additionalAmount = dailyRateTotal.multiply(BigDecimal.valueOf(extraDays));

        RentalExtensionRequest extension = new RentalExtensionRequest(
                reservation,
                request.requestedReturnDate(),
                additionalAmount
        );

        RentalExtensionRequest saved = extensionRepository.save(extension);
        return mapToDTO(saved);
    }

    @Transactional
    public RentalExtensionResponseDTO reviewExtension(UUID extensionId, ReviewExtensionRequestDTO reviewRequest, String reviewerEmail) {
        RentalExtensionRequest extension = extensionRepository.findById(extensionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de extensión no encontrada"));

        User reviewer = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(reviewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario revisor no encontrado"));

        if (!"PENDING".equalsIgnoreCase(extension.getStatus())) {
            throw new ConflictException("La solicitud de extensión ya fue procesada anteriormente con estado: " + extension.getStatus());
        }

        String decision = reviewRequest.status() != null ? reviewRequest.status().toUpperCase() : "";
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("El estado debe ser APPROVED o REJECTED");
        }

        if ("APPROVED".equals(decision)) {
            Reservation reservation = extension.getReservation();

            boolean hasOverlap = reservationRepository.existsOverlappingReservationExcluding(
                    reservation.getVehicle().getId(),
                    reservation.getId(),
                    reservation.getReturnDate(),
                    extension.getRequestedReturnDate()
            );
            if (hasOverlap) {
                throw new ConflictException("No es posible aprobar la extensión porque el vehículo presenta una colisión de fechas con otra reserva.");
            }

            reservation.setReturnDate(extension.getRequestedReturnDate());
            reservation.setTotalEstimated(reservation.getTotalEstimated().add(extension.getAdditionalAmount()));
            reservationRepository.save(reservation);

            extension.setStatus("APPROVED");
        } else {
            extension.setStatus("REJECTED");
        }

        extension.setReviewedBy(reviewer);
        extension.setReviewedAt(Instant.now());

        RentalExtensionRequest saved = extensionRepository.save(extension);
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<RentalExtensionResponseDTO> getExtensionsByReservation(UUID reservationId, String userEmail) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        validateAccess(reservation, user);

        return extensionRepository.findByReservationOrderByCreatedAtDesc(reservation)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public RentalExtensionResponseDTO getExtensionById(UUID extensionId, String userEmail) {
        RentalExtensionRequest extension = extensionRepository.findById(extensionId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de extensión no encontrada"));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        validateAccess(extension.getReservation(), user);

        return mapToDTO(extension);
    }

    @Transactional(readOnly = true)
    public List<RentalExtensionResponseDTO> getAllExtensions(String status) {
        List<RentalExtensionRequest> list;
        if (status != null && !status.isBlank()) {
            list = extensionRepository.findByStatusIgnoreCaseOrderByCreatedAtDesc(status.strip());
        } else {
            list = extensionRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::mapToDTO).toList();
    }

    private void validateAccess(Reservation reservation, User user) {
        boolean isOwner = reservation.getCustomer().getId().equals(user.getId());
        boolean isStaff = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> List.of("ADMIN", "SUPER_ADMIN", "AGENT", "EMPLOYEE", "BRANCH_ADMIN").contains(r.getCode()));

        if (!isOwner && !isStaff) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
    }

    private RentalExtensionResponseDTO mapToDTO(RentalExtensionRequest ext) {
        Reservation r = ext.getReservation();
        long extraSeconds = Duration.between(r.getReturnDate(), ext.getRequestedReturnDate()).toSeconds();
        int extraDays = (int) Math.max(1, (extraSeconds + 86399) / 86400);

        BigDecimal vehicleDailyRate = r.getVehicle() != null ? r.getVehicle().getDailyRate() : BigDecimal.ZERO;
        BigDecimal insuranceDailyRate = r.getInsuranceCoverage() != null ? r.getInsuranceCoverage().getDailyRate() : BigDecimal.ZERO;
        BigDecimal mileageDailyRate = r.getMileagePlan() != null ? r.getMileagePlan().getDailyRate() : BigDecimal.ZERO;

        String reviewerName = null;
        UUID reviewerId = null;
        if (ext.getReviewedBy() != null) {
            reviewerId = ext.getReviewedBy().getId();
            reviewerName = ext.getReviewedBy().getFirstName() + " " + ext.getReviewedBy().getLastName();
        }

        return new RentalExtensionResponseDTO(
                ext.getId(),
                r.getId(),
                r.getCode(),
                r.getReturnDate(),
                ext.getRequestedReturnDate(),
                extraDays,
                vehicleDailyRate,
                insuranceDailyRate,
                mileageDailyRate,
                ext.getAdditionalAmount(),
                ext.getStatus(),
                reviewerId,
                reviewerName,
                ext.getReviewedAt(),
                ext.getCreatedAt(),
                ext.getUpdatedAt()
        );
    }
}
