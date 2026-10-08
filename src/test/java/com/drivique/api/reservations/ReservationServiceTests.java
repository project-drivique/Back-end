package com.drivique.api.reservations;

import com.drivique.api.dto.*;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.PromotionValidationService;
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

class ReservationServiceTests {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ReservationStatusRepository reservationStatusRepository = mock(ReservationStatusRepository.class);
    private final ReservationAdditionalServiceRepository additionalServiceRepository = mock(ReservationAdditionalServiceRepository.class);
    private final ReservationPromotionRepository promotionRepository = mock(ReservationPromotionRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InsuranceCoverageRepository insuranceCoverageRepository = mock(InsuranceCoverageRepository.class);
    private final MileagePlanRepository mileagePlanRepository = mock(MileagePlanRepository.class);
    private final AdditionalServiceRepository additionalServiceCatalogRepository = mock(AdditionalServiceRepository.class);
    private final PromotionRepository promotionCatalogRepository = mock(PromotionRepository.class);
    private final UserCouponUsageRepository userCouponUsageRepository = mock(UserCouponUsageRepository.class);
    private final PromotionValidationService promotionValidationService = mock(PromotionValidationService.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    private final ReservationService service = new ReservationService(
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
            auditLogRepository
    );

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID insuranceId = UUID.randomUUID();
    private final UUID mileagePlanId = UUID.randomUUID();
    private final UUID additionalServiceId = UUID.randomUUID();
    private final UUID promotionId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID reservationId = UUID.randomUUID();
    private final String userEmail = "client@drivique.com";

    private User user;
    private Vehicle vehicle;
    private VehicleCategory category;
    private VehicleStatus vehicleStatus;
    private InsuranceCoverage insurance;
    private MileagePlan mileagePlan;
    private AdditionalService additionalService;
    private Promotion promotion;
    private Branch branch;
    private ReservationStatus pendingStatus;

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
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        pendingStatus = mock(ReservationStatus.class);
        when(pendingStatus.getCode()).thenReturn("PENDING_PAYMENT");
        when(pendingStatus.getName()).thenReturn("Pending payment");
        when(pendingStatus.isBlocksAvailability()).thenReturn(true);
        when(reservationStatusRepository.findByCodeIgnoreCase("PENDING_PAYMENT")).thenReturn(Optional.of(pendingStatus));

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

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
        assertThat(response.totalEstimated()).isEqualByComparingTo("864000");

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

    @Test
    void getReservationById_Success() {
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        ReservationResponseDTO response = service.getReservationById(reservationId, userEmail);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("RES-2026-0001");
    }

    @Test
    void getReservationById_ThrowsNotFound_WhenIdDoesNotExist() {
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReservationById(reservationId, userEmail))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reserva no encontrada");
    }

    @Test
    void getReservationByCode_Success() {
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                true
        );
        when(reservationRepository.findByCodeIgnoreCase("RES-2026-0001")).thenReturn(Optional.of(r));

        ReservationResponseDTO response = service.getReservationByCode("RES-2026-0001", userEmail);

        assertThat(response.code()).isEqualTo("RES-2026-0001");
    }

    @Test
    void cancelReservation_Success() {
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                true
        );
        ReservationStatus cancelledStatus = new ReservationStatus("CANCELLED_BY_USER", "Cancelled by user", false);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(reservationStatusRepository.findByCodeIgnoreCase("CANCELLED_BY_USER")).thenReturn(Optional.of(cancelledStatus));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(r);

        CancelReservationRequestDTO cancelReq = new CancelReservationRequestDTO("User requested cancellation");
        ReservationResponseDTO resp = service.cancelReservation(reservationId, cancelReq, userEmail);

        assertThat(resp).isNotNull();
        assertThat(r.getStatus().getCode()).isEqualTo("CANCELLED_BY_USER");
    }

    @Test
    void cancelReservation_ThrowsConflict_WhenAlreadyFinalState() {
        ReservationStatus completedStatus = new ReservationStatus("COMPLETED", "Completed", false);
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                completedStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                false
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        CancelReservationRequestDTO cancelReq = new CancelReservationRequestDTO("Already done");
        assertThatThrownBy(() -> service.cancelReservation(reservationId, cancelReq, userEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("estado final");
    }

    @Test
    void getAllowedTransitions_Success() {
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));

        AllowedTransitionsResponseDTO result = service.getAllowedTransitions(reservationId, userEmail);

        assertThat(result).isNotNull();
        assertThat(result.allowedTransitions()).contains("CONFIRMED", "CANCELLED_BY_USER");
    }

    @Test
    void modifyReservationDates_Success() {
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(r);

        ModifyReservationDatesRequestDTO modReq = new ModifyReservationDatesRequestDTO(
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS)
        );

        ReservationResponseDTO response = service.modifyReservationDates(reservationId, modReq, userEmail);
        assertThat(response).isNotNull();
    }

    @Test
    void modifyReservationServices_Success() {
        Reservation r = new Reservation(
                "RES-2026-0001",
                user,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("800000"),
                null,
                null,
                true
        );
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(r);

        ModifyReservationServicesRequestDTO req = new ModifyReservationServicesRequestDTO(List.of(additionalServiceId), insuranceId, mileagePlanId);
        ReservationResponseDTO response = service.modifyReservationServices(reservationId, req, userEmail);
        assertThat(response).isNotNull();
    }
}

