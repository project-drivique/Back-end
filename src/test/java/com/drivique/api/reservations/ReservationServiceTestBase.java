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

abstract class ReservationServiceTestBase {

    protected final ReservationRepository reservationRepository = mock(ReservationRepository.class);

    protected final ReservationStatusRepository reservationStatusRepository = mock(ReservationStatusRepository.class);

    protected final ReservationAdditionalServiceRepository additionalServiceRepository = mock(ReservationAdditionalServiceRepository.class);

    protected final ReservationPromotionRepository promotionRepository = mock(ReservationPromotionRepository.class);

    protected final VehicleRepository vehicleRepository = mock(VehicleRepository.class);

    protected final UserRepository userRepository = mock(UserRepository.class);

    protected final InsuranceCoverageRepository insuranceCoverageRepository = mock(InsuranceCoverageRepository.class);

    protected final MileagePlanRepository mileagePlanRepository = mock(MileagePlanRepository.class);

    protected final AdditionalServiceRepository additionalServiceCatalogRepository = mock(AdditionalServiceRepository.class);

    protected final PromotionRepository promotionCatalogRepository = mock(PromotionRepository.class);

    protected final UserCouponUsageRepository userCouponUsageRepository = mock(UserCouponUsageRepository.class);

    protected final PromotionValidationService promotionValidationService = mock(PromotionValidationService.class);

    protected final BranchRepository branchRepository = mock(BranchRepository.class);

    protected final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    protected final NotificationService notificationService = mock(NotificationService.class);

    protected final ReservationDocumentReviewService reservationDocumentReviewService = mock(ReservationDocumentReviewService.class);

    protected final ReservationService service = new ReservationService(
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
            reservationDocumentReviewService
    );

    protected final UUID vehicleId = UUID.randomUUID();

    protected final UUID insuranceId = UUID.randomUUID();

    protected final UUID mileagePlanId = UUID.randomUUID();

    protected final UUID additionalServiceId = UUID.randomUUID();

    protected final UUID promotionId = UUID.randomUUID();

    protected final UUID branchId = UUID.randomUUID();

    protected final UUID reservationId = UUID.randomUUID();

    protected final String userEmail = "client@drivique.com";

    protected User user;

    protected Vehicle vehicle;

    protected VehicleCategory category;

    protected VehicleStatus vehicleStatus;

    protected InsuranceCoverage insurance;

    protected MileagePlan mileagePlan;

    protected AdditionalService additionalService;

    protected Promotion promotion;

    protected Branch branch;

    protected ReservationStatus pendingStatus;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getEmail()).thenReturn(userEmail);
        when(user.getFirstName()).thenReturn("Carlos");
        when(user.getLastName()).thenReturn("Gomez");
        when(user.getRoles()).thenReturn(Collections.emptySet());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(user));

        category = mock(VehicleCategory.class);
        when(category.getId()).thenReturn(UUID.randomUUID());

        vehicleStatus = mock(VehicleStatus.class);
        when(vehicleStatus.allowsReservation()).thenReturn(true);

        vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(vehicleId);
        when(vehicle.getModel()).thenReturn("Corolla Cross");
        when(vehicle.getPlate()).thenReturn("DRV777");
        when(vehicle.getDailyRate()).thenReturn(new BigDecimal("220000"));
        when(vehicle.isActive()).thenReturn(true);
        when(vehicle.getStatus()).thenReturn(vehicleStatus);
        when(vehicle.getCategory()).thenReturn(category);
        when(vehicleRepository.findByIdWithPessimisticLock(vehicleId)).thenReturn(Optional.of(vehicle));

        insurance = mock(InsuranceCoverage.class);
        when(insurance.getId()).thenReturn(insuranceId);
        when(insurance.getName()).thenReturn("Cobertura Total");
        when(insurance.getDailyRate()).thenReturn(new BigDecimal("45000"));
        when(insurance.isActive()).thenReturn(true);
        when(insuranceCoverageRepository.findById(insuranceId)).thenReturn(Optional.of(insurance));

        mileagePlan = mock(MileagePlan.class);
        when(mileagePlan.getId()).thenReturn(mileagePlanId);
        when(mileagePlan.getName()).thenReturn("Ilimitado");
        when(mileagePlan.getDailyRate()).thenReturn(new BigDecimal("30000"));
        when(mileagePlan.isActive()).thenReturn(true);
        when(mileagePlanRepository.findById(mileagePlanId)).thenReturn(Optional.of(mileagePlan));

        additionalService = mock(AdditionalService.class);
        when(additionalService.getId()).thenReturn(additionalServiceId);
        when(additionalService.getName()).thenReturn("Silla para Bebe");
        when(additionalService.getDailyRate()).thenReturn(new BigDecimal("15000"));
        when(additionalService.isActive()).thenReturn(true);
        when(additionalServiceCatalogRepository.findById(additionalServiceId)).thenReturn(Optional.of(additionalService));

        promotion = mock(Promotion.class);
        when(promotion.getId()).thenReturn(promotionId);
        when(promotion.getCode()).thenReturn("DESC10");
        when(promotionCatalogRepository.findByCodeIgnoreCase("DESC10")).thenReturn(Optional.of(promotion));

        branch = mock(Branch.class);
        when(branch.getId()).thenReturn(branchId);
        when(branch.isActive()).thenReturn(true);
        when(branch.allowsCashPayment()).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        pendingStatus = mock(ReservationStatus.class);
        when(pendingStatus.getCode()).thenReturn("PENDING_PAYMENT");
        when(pendingStatus.getName()).thenReturn("Pending payment");
        when(pendingStatus.isBlocksAvailability()).thenReturn(true);
        when(reservationStatusRepository.findByCodeIgnoreCase("PENDING_PAYMENT")).thenReturn(Optional.of(pendingStatus));

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationDocumentReviewService.hasApprovedRequiredDocuments(any())).thenReturn(true);
    }

    protected Reservation openRequestOfTheSameTrip(Instant pickup, Instant returnDate) {
        Reservation open = new Reservation(
                "HLD-OPEN-0001", user, vehicle, pendingStatus, insurance, mileagePlan, branch,
                pickup, returnDate, new BigDecimal("220000"), new BigDecimal("800000"), null, null, true
        );
        open.setId(UUID.randomUUID());
        when(reservationRepository.findOpenRequestsForSameTrip(any(), eq(vehicleId), eq(pickup), eq(returnDate)))
                .thenReturn(List.of(open));
        when(additionalServiceRepository.findByReservationId(open.getId())).thenReturn(List.of());
        when(promotionRepository.findByReservationId(open.getId())).thenReturn(List.of());
        return open;
    }

    protected com.drivique.api.model.RentalContract stubPaymentCollaborators() {
        com.drivique.api.model.RentalContract contract = mock(com.drivique.api.model.RentalContract.class);
        when(contract.getId()).thenReturn(UUID.randomUUID());
        com.drivique.api.repository.RentalContractRepository contracts = mock(com.drivique.api.repository.RentalContractRepository.class);
        when(contracts.findByReservationId(any())).thenReturn(Optional.of(contract));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "rentalContractRepository", contracts);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "wompiPaymentService", wompiPaymentService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "paymentRepository", paymentRepository);
        return contract;
    }

    protected final com.drivique.api.service.WompiPaymentService wompiPaymentService = mock(com.drivique.api.service.WompiPaymentService.class);

    protected final com.drivique.api.repository.PaymentRepository paymentRepository = mock(com.drivique.api.repository.PaymentRepository.class);
}
