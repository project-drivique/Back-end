package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    @Autowired(required = false)
    private DeliveryNotificationService deliveryNotices;

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
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final ReservationDocumentReviewService reservationDocumentReviewService;

    @Autowired(required = false)
    private ReservationPricing reservationPricing = new ReservationPricing(new BigDecimal("0.10"), new BigDecimal("0.19"));

    @Autowired(required = false)
    private RentalContractRepository rentalContractRepository;

    @Autowired(required = false)
    private ContractStatusRepository contractStatusRepository;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    @Autowired(required = false)
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired(required = false)
    private PaymentStatusRepository paymentStatusRepository;

    @Autowired(required = false)
    private CurrencyRepository currencyRepository;

    @Autowired(required = false)
    private ContractService contractService;

    @Autowired(required = false)
    private WompiPaymentService wompiPaymentService;

    @Autowired(required = false)
    private BranchUserRepository branchUserRepository;

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
            BranchRepository branchRepository,
            AuditLogRepository auditLogRepository,
            NotificationService notificationService
    ) {
        this(
                reservationRepository,
                reservationStatusRepository,
                additionalServiceRepository,
                promotionRepository,
                vehicleRepository,
                userRepository,
                insuranceCoverageRepository,
                mileagePlanRepository,
                additionalServiceCatalogRepository,
                promotionCatalogRepository,
                userCouponUsageRepository,
                promotionValidationService,
                branchRepository,
                auditLogRepository,
                notificationService,
                null
        );
    }

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
            BranchRepository branchRepository,
            AuditLogRepository auditLogRepository,
            NotificationService notificationService,
            @Autowired(required = false) ReservationDocumentReviewService reservationDocumentReviewService
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
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.reservationDocumentReviewService = reservationDocumentReviewService;
    }

    @Transactional
    public ReservationResponseDTO createReservation(CreateReservationRequestDTO request, String userEmail) {
        User customer = findUser(userEmail, "Usuario autenticado no encontrado");
        return ReservationCreationHelper.create(
                request,
                customer,
                userEmail,
                reservationRepository,
                reservationStatusRepository,
                additionalServiceRepository,
                promotionRepository,
                vehicleRepository,
                insuranceCoverageRepository,
                mileagePlanRepository,
                additionalServiceCatalogRepository,
                promotionCatalogRepository,
                userCouponUsageRepository,
                promotionValidationService,
                branchRepository,
                notificationService,
                reservationDocumentReviewService,
                reservationPricing
        );
    }

    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(UUID id, String userEmail) {
        Reservation r = findReservationOrThrow(id);
        User user = findUser(userEmail, "Usuario no encontrado");
        if (!r.getCustomer().getId().equals(user.getId()) && !isStaffFor(user, r)) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
        return mapFromEntity(r);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationByCode(String code, String userEmail) {
        Reservation r = reservationRepository.findByCodeIgnoreCase(code.strip())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con código: " + code));
        User user = findUser(userEmail, "Usuario no encontrado");
        if (!r.getCustomer().getId().equals(user.getId()) && !isStaffFor(user, r)) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
        return mapFromEntity(r);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getMyReservations(String userEmail) {
        User user = findUser(userEmail, "Usuario no encontrado");
        return reservationRepository.findByCustomerOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapFromEntity)
                .toList();
    }

    private Reservation findReservationByIdOrCode(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("Identificador de reserva no proporcionado");
        }
        try {
            UUID id = UUID.fromString(identifier.trim());
            Optional<Reservation> byId = reservationRepository.findById(id);
            if (byId.isPresent()) {
                return byId.get();
            }
        } catch (IllegalArgumentException ignored) {
            // No es UUID, se busca por código
        }
        return reservationRepository.findByCodeIgnoreCase(identifier.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Retención o reserva no encontrada: " + identifier));
    }

    @Transactional
    public ReservationResponseDTO confirmReservationPayment(UUID holdId, String userEmail) {
        return confirmReservationPayment(holdId != null ? holdId.toString() : null, null, userEmail);
    }

    @Transactional
    public ReservationResponseDTO confirmReservationPayment(String holdIdentifier, String userEmail) {
        return confirmReservationPayment(holdIdentifier, null, userEmail);
    }

    @Transactional
    public ReservationResponseDTO confirmReservationPayment(String holdIdentifier, String transactionId, String userEmail) {
        Reservation r = findReservationByIdOrCode(holdIdentifier);
        User user = findUser(userEmail, "Usuario no encontrado");
        if (!r.getCustomer().getId().equals(user.getId())) {
            throw new AccessDeniedException("No puede confirmar una reserva de otro usuario");
        }
        if (reservationDocumentReviewService != null) {
            reservationDocumentReviewService.validateCanBePaid(r);
        }

        String cleanTransactionId = transactionId == null ? "" : transactionId.trim();
        if (cleanTransactionId.isEmpty()) {
            throw new ConflictException("Para confirmar la reserva se requiere una transacción de pago aprobada.");
        }
        if (wompiPaymentService == null || rentalContractRepository == null) {
            throw new IllegalStateException("La verificación de pagos no está disponible.");
        }

        RentalContract contract = rentalContractRepository.findByReservationId(r.getId()).orElse(null);
        if (contract == null && contractService != null) {
            contractService.generateContract(new GenerateContractRequestDTO(r.getId()), userEmail);
            contract = rentalContractRepository.findByReservationId(r.getId()).orElse(null);
        }
        if (contract == null) {
            throw new ConflictException("No se pudo preparar el contrato de la reserva para verificar el pago.");
        }

        String currentStatus = r.getStatus() != null ? r.getStatus().getCode() : "";
        boolean alreadyPaid = !r.getCode().startsWith("HLD-")
                && List.of("PENDING_SIGNATURE", "CONFIRMED", "ACTIVE", "COMPLETED").contains(currentStatus.toUpperCase())
                && paymentRepository != null
                && paymentRepository.existsByContractIdAndStatusCodeIgnoreCase(contract.getId(), "APPROVED");
        if (alreadyPaid) {
            return mapFromEntity(r);
        }

        wompiPaymentService.verifyAndRecordApprovedPayment(cleanTransactionId, contract, userEmail);

        if (r.getCode().startsWith("HLD-")) {
            r.setCode(generateUniqueReservationCode());
        }

        boolean isContractSigned = contract.getStatus() != null && "ACTIVE".equalsIgnoreCase(contract.getStatus().getCode());
        String targetStatusCode = isContractSigned ? "CONFIRMED" : "PENDING_SIGNATURE";
        ReservationStatus targetStatus = reservationStatusRepository.findByCodeIgnoreCase(targetStatusCode)
                .or(() -> reservationStatusRepository.findByCodeIgnoreCase("PENDING_SIGNATURE"))
                .or(() -> reservationStatusRepository.findByCodeIgnoreCase("CONFIRMED"))
                .orElseThrow(() -> new IllegalStateException("Estado " + targetStatusCode + " no encontrado"));
        r.setStatus(targetStatus);

        Reservation saved = reservationRepository.save(r);
        avisarDomicilioPagado(saved);

        if (contractStatusRepository != null && contract.getStatus() != null && "DRAFT".equalsIgnoreCase(contract.getStatus().getCode())) {
            contractStatusRepository.findByCodeIgnoreCase("PENDING_SIGNATURE").ifPresent(contract::setStatus);
        }
        rentalContractRepository.saveAndFlush(contract);

        ReservationEventPublisher.recordAudit(
                auditLogRepository, r.getId(), user, "Reservation confirmed after payment verification");

        boolean hasApprovedPayment = paymentRepository != null
                && rentalContractRepository.findByReservationId(saved.getId())
                        .map(c -> paymentRepository.existsByContractIdAndStatusCodeIgnoreCase(c.getId(), "APPROVED"))
                        .orElse(false);
        ReservationEventPublisher.notifyPaymentConfirmed(notificationService, saved, hasApprovedPayment);

        return mapFromEntity(saved);
    }

    private void avisarDomicilioPagado(Reservation reservation) {
        try {
            if (deliveryNotices != null) {
                deliveryNotices.homeDeliveryPaid(reservation);
            }
        } catch (RuntimeException ex) {
            log.debug("Aviso de domicilio pagado omitido: {}", ex.getMessage());
        }
    }

    @Transactional
    public void reportPaymentFailed(UUID holdId, String userEmail) {
        reportPaymentFailed(holdId != null ? holdId.toString() : null, userEmail);
    }

    @Transactional
    public void reportPaymentFailed(String holdIdentifier, String userEmail) {
        if (holdIdentifier == null || holdIdentifier.isBlank()) return;
        Reservation r;
        try {
            r = findReservationByIdOrCode(holdIdentifier);
        } catch (Exception ignored) {
            return;
        }
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail).orElse(null);
        if (user == null || !r.getCustomer().getId().equals(user.getId())) return;
        ReservationEventPublisher.notifyPaymentFailed(notificationService, user, r);
    }

    boolean isStaffFor(User user, Reservation r) {
        Set<String> codes = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        if (codes.stream().anyMatch(c -> List.of("ADMIN", "SUPER_ADMIN", "AGENT").contains(c))) {
            return true;
        }
        if (codes.stream().noneMatch(c -> List.of("EMPLOYEE", "BRANCH_ADMIN").contains(c)) || branchUserRepository == null) {
            return false;
        }
        return branchUserRepository.findByUserId(user.getId()).map(b -> {
            UUID branchId = b.getBranchId();
            boolean vehicleBranch = r.getVehicle() != null && r.getVehicle().getCurrentBranch() != null
                    && branchId.equals(r.getVehicle().getCurrentBranch().getId());
            boolean cashBranch = r.getCashPaymentBranch() != null && branchId.equals(r.getCashPaymentBranch().getId());
            return vehicleBranch || cashBranch;
        }).orElse(false);
    }

    @Transactional
    public ReservationResponseDTO cancelReservation(UUID id, CancelReservationRequestDTO request, String userEmail) {
        Reservation r = findReservationOrThrow(id);
        User user = findUser(userEmail, "Usuario no encontrado");
        ensureOwnerOrStaff(user, r, "No tiene permisos para cancelar esta reserva");

        if (List.of("CANCELLED_BY_USER", "CANCELLED_BY_TIMEOUT", "REJECTED", "COMPLETED").contains(r.getStatus().getCode())) {
            throw new ConflictException("La reserva ya se encuentra en un estado final y no puede ser cancelada");
        }

        ReservationStatus cancelledStatus = reservationStatusRepository.findByCodeIgnoreCase("CANCELLED_BY_USER")
                .orElseThrow(() -> new IllegalStateException("Estado CANCELLED_BY_USER no encontrado"));
        r.setStatus(cancelledStatus);
        if (r.getDeliveryPoints() != null) {
            for (var pt : r.getDeliveryPoints()) {
                pt.cancelDelivery();
            }
        }
        Reservation saved = reservationRepository.save(r);
        ReservationEventPublisher.recordAudit(
                auditLogRepository, r.getId(), user, "Reservation cancelled. Reason: " + request.reason());
        ReservationEventPublisher.notifyCancellation(notificationService, saved, request.reason());
        return mapFromEntity(saved);
    }

    @Transactional(readOnly = true)
    public AllowedTransitionsResponseDTO getAllowedTransitions(UUID id, String userEmail) {
        Reservation r = findReservationOrThrow(id);
        User user = findUser(userEmail, "Usuario no encontrado");
        ensureOwnerOrStaff(user, r, "No tiene permisos");
        String currentStatusCode = r.getStatus().getCode();
        return new AllowedTransitionsResponseDTO(
                r.getId(), currentStatusCode, ReservationResponseMapper.allowedTransitionsFor(currentStatusCode));
    }

    @Transactional
    public ReservationResponseDTO modifyReservationDates(UUID id, ModifyReservationDatesRequestDTO request, String userEmail) {
        Reservation r = findReservationOrThrow(id);
        User user = findUser(userEmail, "Usuario no encontrado");
        ensureOwnerOrStaff(user, r, "No tiene permisos");

        if (List.of("CANCELLED_BY_USER", "CANCELLED_BY_TIMEOUT", "REJECTED", "COMPLETED").contains(r.getStatus().getCode())) {
            throw new ConflictException("No se pueden modificar fechas de una reserva en estado final");
        }
        if (request.pickupDate() == null || request.returnDate() == null) {
            throw new IllegalArgumentException("Las fechas son obligatorias");
        }
        ReservationCreationHelper.validatePickupAndReturnDates(request.pickupDate(), request.returnDate(), false);

        if (reservationRepository.existsOverlappingReservationExcluding(
                r.getVehicle().getId(), r.getId(), request.pickupDate(), request.returnDate())) {
            throw new ConflictException("El vehículo no está disponible para las fechas seleccionadas");
        }

        r.setPickupDate(request.pickupDate());
        r.setReturnDate(request.returnDate());

        int rentalDays = ReservationCreationHelper.calculateRentalDays(request.pickupDate(), request.returnDate());
        BigDecimal multiplier = BigDecimal.valueOf(rentalDays);
        BigDecimal vehicleSubtotal = r.getVehicle().getDailyRate().multiply(multiplier);
        BigDecimal insuranceSubtotal = r.getInsuranceCoverage().getDailyRate().multiply(multiplier);
        BigDecimal mileageSubtotal = r.getMileagePlan().getDailyRate().multiply(multiplier);
        BigDecimal extrasSubtotal = r.getAdditionalServices().stream()
                .map(s -> s.getDailyRate().multiply(multiplier).multiply(BigDecimal.valueOf(s.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountApplied = r.getPromotions().stream()
                .map(ReservationPromotion::getDiscountApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEstimated = reservationPricing.calculate(
                vehicleSubtotal.add(insuranceSubtotal).add(mileageSubtotal).add(extrasSubtotal), discountApplied).total();
        r.setTotalEstimated(totalEstimated);

        Reservation saved = reservationRepository.save(r);
        ReservationEventPublisher.recordAudit(auditLogRepository, r.getId(), user, "Dates modified");
        return mapFromEntity(saved);
    }

    @Transactional
    public ReservationResponseDTO modifyReservationServices(UUID id, ModifyReservationServicesRequestDTO request, String userEmail) {
        Reservation r = findReservationOrThrow(id);
        User user = findUser(userEmail, "Usuario no encontrado");
        ensureOwnerOrStaff(user, r, "No tiene permisos");

        if (List.of("CANCELLED_BY_USER", "CANCELLED_BY_TIMEOUT", "REJECTED", "COMPLETED").contains(r.getStatus().getCode())) {
            throw new ConflictException("No se pueden modificar servicios de una reserva en estado final");
        }
        if (request.insuranceCoverageId() != null) {
            InsuranceCoverage insurance = insuranceCoverageRepository.findById(request.insuranceCoverageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cobertura no encontrada"));
            r.setInsuranceCoverage(insurance);
        }
        if (request.mileagePlanId() != null) {
            MileagePlan plan = mileagePlanRepository.findById(request.mileagePlanId())
                    .orElseThrow(() -> new ResourceNotFoundException("Plan de kilometraje no encontrado"));
            r.setMileagePlan(plan);
        }

        Reservation saved = reservationRepository.save(r);
        ReservationEventPublisher.recordAudit(auditLogRepository, r.getId(), user, "Services modified");
        return mapFromEntity(saved);
    }

    private Reservation findReservationOrThrow(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
    }

    private User findUser(String userEmail, String message) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException(message));
    }

    private void ensureOwnerOrStaff(User user, Reservation r, String deniedMessage) {
        if (!r.getCustomer().getId().equals(user.getId()) && !isStaffFor(user, r)) {
            throw new AccessDeniedException(deniedMessage);
        }
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

    public ReservationResponseDTO toResponse(Reservation r) {
        return mapFromEntity(r);
    }

    private ReservationResponseDTO mapFromEntity(Reservation r) {
        return ReservationResponseMapper.mapFromEntity(r, reservationPricing);
    }
}
