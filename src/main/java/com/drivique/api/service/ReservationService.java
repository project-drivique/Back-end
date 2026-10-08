package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationStatusRepository reservationStatusRepository;
    private final ReservationAdditionalServiceRepository additionalServiceRepository;
    private final ReservationPromotionRepository promotionRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final InsuranceCoverageRepository insuranceCoverageRepository;
    private final MileagePlanRepository mileagePlanRepository;
    private final AdditionalServiceRepository additionalServiceCatalogRepository;
    private final PromotionRepository promotionCatalogRepository;
    private final UserCouponUsageRepository userCouponUsageRepository;
    private final PromotionValidationService promotionValidationService;
    private final BranchRepository branchRepository;

    @Autowired
    public ReservationService(
            ReservationRepository reservationRepository,
            ReservationStatusRepository reservationStatusRepository,
            ReservationAdditionalServiceRepository additionalServiceRepository,
            ReservationPromotionRepository promotionRepository,
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            InsuranceCoverageRepository insuranceCoverageRepository,
            MileagePlanRepository mileagePlanRepository,
            AdditionalServiceRepository additionalServiceCatalogRepository,
            PromotionRepository promotionCatalogRepository,
            UserCouponUsageRepository userCouponUsageRepository,
            PromotionValidationService promotionValidationService,
            BranchRepository branchRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationStatusRepository = reservationStatusRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.promotionRepository = promotionRepository;
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.insuranceCoverageRepository = insuranceCoverageRepository;
        this.mileagePlanRepository = mileagePlanRepository;
        this.additionalServiceCatalogRepository = additionalServiceCatalogRepository;
        this.promotionCatalogRepository = promotionCatalogRepository;
        this.userCouponUsageRepository = userCouponUsageRepository;
        this.promotionValidationService = promotionValidationService;
        this.branchRepository = branchRepository;
    }

    @Transactional
    public ReservationResponseDTO createReservation(CreateReservationRequestDTO request, String userEmail) {
        User customer = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        if (request.pickupDate() == null || request.returnDate() == null) {
            throw new IllegalArgumentException("Las fechas de recogida y devolución son obligatorias");
        }

        if (!request.returnDate().isAfter(request.pickupDate())) {
            throw new IllegalArgumentException("La fecha de devolución debe ser posterior a la fecha de recogida");
        }

        if (request.pickupDate().isBefore(Instant.now().minus(Duration.ofMinutes(15)))) {
            throw new IllegalArgumentException("La fecha de recogida no puede ser en el pasado");
        }

        long seconds = Duration.between(request.pickupDate(), request.returnDate()).toSeconds();
        int rentalDays = (int) Math.max(1, (seconds + 86399) / 86400);

        Vehicle vehicle = vehicleRepository.findByIdWithPessimisticLock(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehículo activo no encontrado"));

        if (!vehicle.isActive() || vehicle.getStatus() == null || !vehicle.getStatus().allowsReservation()) {
            throw new ConflictException("El vehículo no está habilitado para reservas");
        }

        boolean hasOverlap = reservationRepository.existsOverlappingReservation(
                vehicle.getId(),
                request.pickupDate(),
                request.returnDate()
        );
        if (hasOverlap) {
            throw new ConflictException("El vehículo no se encuentra disponible en las fechas seleccionadas debido a otra reserva activa");
        }

        InsuranceCoverage insurance = insuranceCoverageRepository.findById(request.insuranceCoverageId())
                .filter(InsuranceCoverage::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Cobertura de seguro no encontrada o inactiva"));

        MileagePlan mileagePlan = mileagePlanRepository.findById(request.mileagePlanId())
                .filter(MileagePlan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de kilometraje no encontrado o inactivo"));

        Branch cashBranch = null;
        if (request.cashPaymentBranchId() != null) {
            cashBranch = branchRepository.findById(request.cashPaymentBranchId())
                    .filter(Branch::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Sede de pago en efectivo no encontrada o inactiva"));
        }

        BigDecimal multiplier = BigDecimal.valueOf(rentalDays);
        BigDecimal vehicleSubtotal = vehicle.getDailyRate().multiply(multiplier);
        BigDecimal insuranceSubtotal = insurance.getDailyRate().multiply(multiplier);
        BigDecimal mileageSubtotal = mileagePlan.getDailyRate().multiply(multiplier);

        List<ReservationAdditionalServiceItemDTO> serviceItems = new ArrayList<>();
        BigDecimal extrasSubtotal = BigDecimal.ZERO;

        if (request.additionalServiceIds() != null && !request.additionalServiceIds().isEmpty()) {
            List<UUID> distinctIds = request.additionalServiceIds().stream().filter(Objects::nonNull).distinct().toList();
            for (UUID sId : distinctIds) {
                AdditionalService catalogService = additionalServiceCatalogRepository.findById(sId)
                        .filter(AdditionalService::isActive)
                        .orElseThrow(() -> new ResourceNotFoundException("Servicio adicional activo no encontrado: " + sId));
                short qty = 1;
                BigDecimal serviceRate = catalogService.getDailyRate();
                BigDecimal sub = serviceRate.multiply(multiplier).multiply(BigDecimal.valueOf(qty));
                extrasSubtotal = extrasSubtotal.add(sub);
                serviceItems.add(new ReservationAdditionalServiceItemDTO(
                        catalogService.getId(),
                        catalogService.getName(),
                        qty,
                        serviceRate,
                        sub
                ));
            }
        }

        BigDecimal discountApplied = BigDecimal.ZERO;
        ReservationPromotionItemDTO promoItem = null;
        Promotion appliedPromotion = null;

        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            String cleanCode = request.couponCode().strip();
            LocalDate pDate = request.pickupDate().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate rDate = request.returnDate().atZone(ZoneOffset.UTC).toLocalDate();

            PromotionValidationResponseDTO promoResult = promotionValidationService.validate(
                    new PromotionValidationRequestDTO(cleanCode, vehicle.getId(), vehicle.getCategory().getId(), pDate, rDate),
                    userEmail
            );

            appliedPromotion = promotionCatalogRepository.findByCodeIgnoreCase(cleanCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada: " + cleanCode));

            discountApplied = promoResult.discountAmount();
            promoItem = new ReservationPromotionItemDTO(
                    appliedPromotion.getId(),
                    appliedPromotion.getCode(),
                    discountApplied
            );

            appliedPromotion.incrementUsesCount();
            userCouponUsageRepository.save(new UserCouponUsage(appliedPromotion, customer, discountApplied));
        }

        BigDecimal totalEstimated = vehicleSubtotal
                .add(insuranceSubtotal)
                .add(mileageSubtotal)
                .add(extrasSubtotal)
                .subtract(discountApplied)
                .max(BigDecimal.ZERO);

        String cashPaymentCode = null;
        Instant cashPaymentExpiresAt = null;
        if (cashBranch != null || request.cashPaymentBranchId() != null) {
            cashPaymentCode = "CASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Instant standard72h = Instant.now().plus(72, ChronoUnit.HOURS);
            cashPaymentExpiresAt = standard72h.isBefore(request.pickupDate()) ? standard72h : request.pickupDate();
        }

        String reservationCode = "HLD-" + UUID.randomUUID().toString().substring(0, 15).toUpperCase();

        ReservationStatus initialStatus = reservationStatusRepository.findByCodeIgnoreCase("PENDING_PAYMENT")
                .orElseGet(() -> reservationStatusRepository.save(new ReservationStatus("PENDING_PAYMENT", "Pending payment", true)));

        Reservation reservation = new Reservation(
                reservationCode,
                customer,
                vehicle,
                initialStatus,
                insurance,
                mileagePlan,
                cashBranch,
                request.pickupDate(),
                request.returnDate(),
                vehicle.getDailyRate(),
                totalEstimated,
                cashPaymentCode,
                cashPaymentExpiresAt,
                initialStatus.isBlocksAvailability()
        );

        Reservation savedReservation = reservationRepository.save(reservation);

        for (ReservationAdditionalServiceItemDTO item : serviceItems) {
            AdditionalService s = additionalServiceCatalogRepository.findById(item.additionalServiceId()).orElseThrow();
            ReservationAdditionalService ras = new ReservationAdditionalService(
                    savedReservation,
                    s,
                    item.quantity(),
                    item.dailyRate()
            );
            additionalServiceRepository.save(ras);
            savedReservation.addAdditionalService(ras);
        }

        if (appliedPromotion != null && discountApplied.compareTo(BigDecimal.ZERO) > 0) {
            ReservationPromotion rp = new ReservationPromotion(
                    savedReservation,
                    appliedPromotion,
                    discountApplied
            );
            promotionRepository.save(rp);
            savedReservation.addPromotion(rp);
        }

        return mapToDTO(savedReservation, rentalDays, vehicleSubtotal, serviceItems, promoItem);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(UUID id, String userEmail) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        boolean isOwner = r.getCustomer().getId().equals(user.getId());
        boolean isStaff = user.getRoles().stream()
                .anyMatch(role -> List.of("ADMIN", "SUPER_ADMIN", "AGENT").contains(role.getCode()));

        if (!isOwner && !isStaff) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }

        return mapFromEntity(r);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationByCode(String code, String userEmail) {
        Reservation r = reservationRepository.findByCodeIgnoreCase(code.strip())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con código: " + code));
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        boolean isOwner = r.getCustomer().getId().equals(user.getId());
        boolean isStaff = user.getRoles().stream()
                .anyMatch(role -> List.of("ADMIN", "SUPER_ADMIN", "AGENT").contains(role.getCode()));

        if (!isOwner && !isStaff) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }

        return mapFromEntity(r);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getMyReservations(String userEmail) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return reservationRepository.findByCustomerOrderByCreatedAtDesc(user)
                .stream()
                .filter(r -> !r.getCode().startsWith("HLD-"))
                .map(this::mapFromEntity)
                .toList();
    }

    @Transactional
    public ReservationResponseDTO confirmReservationPayment(UUID holdId, String userEmail) {
        Reservation r = reservationRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Retención no encontrada"));
        
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
                
        if (!r.getCustomer().getId().equals(user.getId())) {
             throw new AccessDeniedException("No puede confirmar una reserva de otro usuario");
        }

        if (!r.getCode().startsWith("HLD-")) {
            // Idempotency: if it's already a RES-, it was already confirmed. Return it.
            return mapFromEntity(r);
        }

        String realCode = generateUniqueReservationCode();
        r.setCode(realCode);
        
        ReservationStatus confirmedStatus = reservationStatusRepository.findByCodeIgnoreCase("CONFIRMED")
                .orElseThrow(() -> new IllegalStateException("Estado CONFIRMED no encontrado"));
        r.setStatus(confirmedStatus);
        
        Reservation saved = reservationRepository.save(r);
        return mapFromEntity(saved);
    }

    private String generateUniqueReservationCode() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        String prefix = "RES-" + year + "-";
        long count = reservationRepository.countByCodeStartingWith(prefix) + 1;
        String code = String.format("RES-%d-%04d", year, count);
        int counter = (int) count;
        while (reservationRepository.findByCode(code).isPresent()) {
            counter++;
            code = String.format("RES-%d-%04d", year, counter);
        }
        return code;
    }

    private ReservationResponseDTO mapFromEntity(Reservation r) {
        long seconds = Duration.between(r.getPickupDate(), r.getReturnDate()).toSeconds();
        int rentalDays = (int) Math.max(1, (seconds + 86399) / 86400);
        BigDecimal vehicleSubtotal = r.getDailyRate().multiply(BigDecimal.valueOf(rentalDays));

        List<ReservationAdditionalServiceItemDTO> services = r.getAdditionalServices().stream()
                .map(s -> new ReservationAdditionalServiceItemDTO(
                        s.getAdditionalService().getId(),
                        s.getAdditionalService().getName(),
                        s.getQuantity(),
                        s.getDailyRate(),
                        s.getDailyRate().multiply(BigDecimal.valueOf(rentalDays)).multiply(BigDecimal.valueOf(s.getQuantity()))
                ))
                .toList();

        ReservationPromotionItemDTO promo = r.getPromotions().stream().findFirst()
                .map(p -> new ReservationPromotionItemDTO(
                        p.getPromotion().getId(),
                        p.getPromotion().getCode(),
                        p.getDiscountApplied()
                ))
                .orElse(null);

        return mapToDTO(r, rentalDays, vehicleSubtotal, services, promo);
    }

    private ReservationResponseDTO mapToDTO(
            Reservation r,
            int rentalDays,
            BigDecimal vehicleSubtotal,
            List<ReservationAdditionalServiceItemDTO> services,
            ReservationPromotionItemDTO promo
    ) {
        List<DeliveryPointResponseDTO> deliveryPointDTOs = r.getDeliveryPoints() != null
                ? r.getDeliveryPoints().stream()
                .map(pt -> new DeliveryPointResponseDTO(
                        pt.getId(),
                        r.getId(),
                        pt.getPointType(),
                        pt.getModality(),
                        pt.getBranch() != null ? pt.getBranch().getId() : null,
                        pt.getBranch() != null ? pt.getBranch().getName() : null,
                        pt.getCity() != null ? pt.getCity().getId() : null,
                        pt.getCity() != null ? pt.getCity().getName() : null,
                        pt.getNeighborhood(),
                        pt.getAddress(),
                        pt.getFlightOrBusNumber(),
                        pt.getReferenceDetails(),
                        pt.getCreatedAt(),
                        pt.getUpdatedAt()
                ))
                .toList()
                : List.of();

        return new ReservationResponseDTO(
                r.getId(),
                r.getCode(),
                r.getCustomer().getId(),
                r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName(),
                r.getCustomer().getEmail(),
                r.getVehicle().getId(),
                r.getVehicle().getModel(),
                r.getVehicle().getPlate(),
                r.getStatus().getCode(),
                r.getStatus().getName(),
                r.getPickupDate(),
                r.getReturnDate(),
                rentalDays,
                r.getDailyRate(),
                vehicleSubtotal,
                r.getInsuranceCoverage().getId(),
                r.getInsuranceCoverage().getName(),
                r.getInsuranceCoverage().getDailyRate(),
                r.getMileagePlan().getId(),
                r.getMileagePlan().getName(),
                r.getMileagePlan().getDailyRate(),
                services,
                promo,
                r.getTotalEstimated(),
                r.getCashPaymentBranch() != null ? r.getCashPaymentBranch().getId() : null,
                r.getCashPaymentCode(),
                r.getCashPaymentExpiresAt(),
                r.isBlocksAvailability(),
                deliveryPointDTOs,
                r.getCreatedAt()
        );
    }
}
