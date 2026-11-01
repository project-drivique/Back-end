package com.drivique.api.service;

import com.drivique.api.dto.CreateReservationRequestDTO;
import com.drivique.api.dto.PromotionValidationRequestDTO;
import com.drivique.api.dto.PromotionValidationResponseDTO;
import com.drivique.api.dto.ReservationAdditionalServiceItemDTO;
import com.drivique.api.dto.ReservationPromotionItemDTO;
import com.drivique.api.dto.ReservationResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.AdditionalService;
import com.drivique.api.model.Branch;
import com.drivique.api.model.InsuranceCoverage;
import com.drivique.api.model.MileagePlan;
import com.drivique.api.model.Promotion;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationAdditionalService;
import com.drivique.api.model.ReservationPromotion;
import com.drivique.api.model.ReservationStatus;
import com.drivique.api.model.User;
import com.drivique.api.model.UserCouponUsage;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.AdditionalServiceRepository;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.InsuranceCoverageRepository;
import com.drivique.api.repository.MileagePlanRepository;
import com.drivique.api.repository.PromotionRepository;
import com.drivique.api.repository.ReservationAdditionalServiceRepository;
import com.drivique.api.repository.ReservationPromotionRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.ReservationStatusRepository;
import com.drivique.api.repository.UserCouponUsageRepository;
import com.drivique.api.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ReservationCreationHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationCreationHelper.class);

    private ReservationCreationHelper() {
    }

    static ReservationResponseDTO create(
            CreateReservationRequestDTO request,
            User customer,
            String userEmail,
            ReservationRepository reservationRepository,
            ReservationStatusRepository reservationStatusRepository,
            ReservationAdditionalServiceRepository additionalServiceRepository,
            ReservationPromotionRepository promotionRepository,
            VehicleRepository vehicleRepository,
            InsuranceCoverageRepository insuranceCoverageRepository,
            MileagePlanRepository mileagePlanRepository,
            AdditionalServiceRepository additionalServiceCatalogRepository,
            PromotionRepository promotionCatalogRepository,
            UserCouponUsageRepository userCouponUsageRepository,
            PromotionValidationService promotionValidationService,
            BranchRepository branchRepository,
            NotificationService notificationService,
            ReservationDocumentReviewService reservationDocumentReviewService,
            ReservationPricing reservationPricing
    ) {
        validatePickupAndReturnDates(request.pickupDate(), request.returnDate(), true);

        int rentalDays = calculateRentalDays(request.pickupDate(), request.returnDate());
        Vehicle vehicle = vehicleRepository.findByIdWithPessimisticLock(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehículo activo no encontrado"));

        if (!vehicle.isActive() || vehicle.getStatus() == null || !vehicle.getStatus().allowsReservation()) {
            throw new ConflictException("El vehículo no está habilitado para reservas");
        }

        Reservation duplicate = ReservationResponseMapper.findIdenticalOpenRequest(
                reservationRepository, additionalServiceRepository, promotionRepository, customer, vehicle, request);
        if (duplicate != null) {
            LOG.info("Solicitud de reserva repetida del usuario {}: se devuelve la reserva abierta {}", userEmail, duplicate.getCode());
            return ReservationResponseMapper.mapFromEntity(duplicate, reservationPricing);
        }

        if (reservationRepository.existsOverlappingReservation(vehicle.getId(), request.pickupDate(), request.returnDate())) {
            throw new ConflictException("El vehículo no se encuentra disponible en las fechas seleccionadas debido a otra reserva activa");
        }

        InsuranceCoverage insurance = resolveInsuranceCoverage(insuranceCoverageRepository, request.insuranceCoverageId());
        MileagePlan mileagePlan = resolveMileagePlan(mileagePlanRepository, request.mileagePlanId());
        Branch cashBranch = resolveCashBranch(branchRepository, request.cashPaymentBranchId());

        BigDecimal multiplier = BigDecimal.valueOf(rentalDays);
        BigDecimal vehicleSubtotal = vehicle.getDailyRate().multiply(multiplier);
        BigDecimal insuranceSubtotal = insurance.getDailyRate().multiply(multiplier);
        BigDecimal mileageSubtotal = mileagePlan.getDailyRate().multiply(multiplier);

        List<ReservationAdditionalServiceItemDTO> serviceItems = new ArrayList<>();
        BigDecimal extrasSubtotal = buildAdditionalServiceItems(
                additionalServiceCatalogRepository, request.additionalServiceIds(), multiplier, serviceItems);

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
            promoItem = new ReservationPromotionItemDTO(appliedPromotion.getId(), appliedPromotion.getCode(), discountApplied);
            appliedPromotion.incrementUsesCount();
            userCouponUsageRepository.save(new UserCouponUsage(appliedPromotion, customer, discountApplied));
        }

        BigDecimal totalEstimated = reservationPricing.calculate(
                vehicleSubtotal.add(insuranceSubtotal).add(mileageSubtotal).add(extrasSubtotal), discountApplied).total();

        boolean documentsApproved = reservationDocumentReviewService != null
                && reservationDocumentReviewService.hasApprovedRequiredDocuments(customer.getId());
        Branch pickupBranch = vehicle.getCurrentBranch() != null ? vehicle.getCurrentBranch() : cashBranch;
        boolean isOutsideSchedule = ReservationResponseMapper.isOutsideBranchSchedule(pickupBranch);

        String initialStatusCode = (isOutsideSchedule && Boolean.TRUE.equals(request.outsideScheduleAccepted()))
                ? "PENDING_SCHEDULE"
                : (documentsApproved ? "PENDING_PAYMENT" : "PENDING_DOCUMENT_REVIEW");

        String cashPaymentCode = (cashBranch != null || request.cashPaymentBranchId() != null)
                ? "CASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : null;
        Instant cashPaymentExpiresAt = "PENDING_PAYMENT".equals(initialStatusCode)
                ? ReservationExpirationService.paymentDeadline(Instant.now(), request.pickupDate())
                : ReservationExpirationService.waitingDeadline(Instant.now(), request.pickupDate());

        String reservationCode = "HLD-" + UUID.randomUUID().toString().substring(0, 15).toUpperCase();
        ReservationStatus initialStatus = reservationStatusRepository.findByCodeIgnoreCase(initialStatusCode)
                .orElseGet(() -> reservationStatusRepository.save(new ReservationStatus(initialStatusCode, initialStatusCode, true)));

        Reservation savedReservation = reservationRepository.save(new Reservation(
                reservationCode, customer, vehicle, initialStatus, insurance, mileagePlan, cashBranch,
                request.pickupDate(), request.returnDate(), vehicle.getDailyRate(), totalEstimated,
                cashPaymentCode, cashPaymentExpiresAt, initialStatus.isBlocksAvailability()
        ));

        ReservationEventPublisher.notifyInitialCreation(
                notificationService, savedReservation, initialStatusCode, pickupBranch, documentsApproved);
        persistAdditionalServicesAndPromotion(
                additionalServiceCatalogRepository, additionalServiceRepository, promotionRepository,
                savedReservation, serviceItems, appliedPromotion, discountApplied);

        return ReservationResponseMapper.mapToDTO(
                savedReservation, rentalDays, vehicleSubtotal, serviceItems, promoItem, reservationPricing);
    }

    static void validatePickupAndReturnDates(Instant pickupDate, Instant returnDate, boolean checkNotPast) {
        if (pickupDate == null || returnDate == null) {
            throw new IllegalArgumentException("Las fechas de recogida y devolución son obligatorias");
        }
        if (!returnDate.isAfter(pickupDate)) {
            throw new IllegalArgumentException("La fecha de devolución debe ser posterior a la fecha de recogida");
        }
        if (checkNotPast && pickupDate.isBefore(Instant.now().minus(Duration.ofHours(6)))) {
            throw new IllegalArgumentException("La fecha de recogida no puede ser en el pasado");
        }
    }

    static int calculateRentalDays(Instant pickupDate, Instant returnDate) {
        long seconds = Duration.between(pickupDate, returnDate).toSeconds();
        return (int) Math.max(1, (seconds + 86399) / 86400);
    }

    private static InsuranceCoverage resolveInsuranceCoverage(InsuranceCoverageRepository repo, UUID coverageId) {
        return (coverageId == null
                ? repo.findAll().stream().filter(InsuranceCoverage::isActive)
                        .min(Comparator.comparing(InsuranceCoverage::getDailyRate))
                : repo.findById(coverageId))
                .filter(InsuranceCoverage::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Cobertura de seguro no encontrada o inactiva"));
    }

    private static MileagePlan resolveMileagePlan(MileagePlanRepository repo, UUID planId) {
        return (planId == null
                ? repo.findAll().stream().filter(MileagePlan::isActive)
                        .min(Comparator.comparing(MileagePlan::getDailyRate))
                : repo.findById(planId))
                .filter(MileagePlan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan de kilometraje no encontrado o inactivo"));
    }

    private static Branch resolveCashBranch(BranchRepository repo, UUID cashBranchId) {
        if (cashBranchId == null) {
            return null;
        }
        Branch cashBranch = repo.findById(cashBranchId)
                .filter(Branch::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Sede de pago en efectivo no encontrada o inactiva"));
        if (!cashBranch.allowsCashPayment()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La sucursal " + cashBranch.getName() + " no recibe pagos en efectivo. Elige otro método de pago.");
        }
        return cashBranch;
    }

    private static BigDecimal buildAdditionalServiceItems(
            AdditionalServiceRepository catalogRepo,
            List<UUID> serviceIds,
            BigDecimal multiplier,
            List<ReservationAdditionalServiceItemDTO> serviceItems
    ) {
        BigDecimal extrasSubtotal = BigDecimal.ZERO;
        if (serviceIds == null || serviceIds.isEmpty()) {
            return extrasSubtotal;
        }
        for (UUID sId : serviceIds.stream().filter(Objects::nonNull).distinct().toList()) {
            AdditionalService catalogService = catalogRepo.findById(sId)
                    .filter(AdditionalService::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Servicio adicional activo no encontrado: " + sId));
            short qty = 1;
            BigDecimal serviceRate = catalogService.getDailyRate();
            BigDecimal sub = serviceRate.multiply(multiplier).multiply(BigDecimal.valueOf(qty));
            extrasSubtotal = extrasSubtotal.add(sub);
            serviceItems.add(new ReservationAdditionalServiceItemDTO(
                    catalogService.getId(), catalogService.getName(), qty, serviceRate, sub));
        }
        return extrasSubtotal;
    }

    private static void persistAdditionalServicesAndPromotion(
            AdditionalServiceRepository catalogRepo,
            ReservationAdditionalServiceRepository additionalServiceRepository,
            ReservationPromotionRepository promotionRepository,
            Reservation savedReservation,
            List<ReservationAdditionalServiceItemDTO> serviceItems,
            Promotion appliedPromotion,
            BigDecimal discountApplied
    ) {
        for (ReservationAdditionalServiceItemDTO item : serviceItems) {
            AdditionalService s = catalogRepo.findById(item.additionalServiceId()).orElseThrow();
            ReservationAdditionalService ras = new ReservationAdditionalService(
                    savedReservation, s, item.quantity(), item.dailyRate());
            additionalServiceRepository.save(ras);
            savedReservation.addAdditionalService(ras);
        }
        if (appliedPromotion != null && discountApplied.compareTo(BigDecimal.ZERO) > 0) {
            ReservationPromotion rp = new ReservationPromotion(savedReservation, appliedPromotion, discountApplied);
            promotionRepository.save(rp);
            savedReservation.addPromotion(rp);
        }
    }
}
