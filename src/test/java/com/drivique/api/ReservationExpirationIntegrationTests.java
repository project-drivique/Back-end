package com.drivique.api;

import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.ReservationExpirationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ReservationExpirationIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired private UserRepository userRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private VehicleBrandRepository brandRepository;
    @Autowired private VehicleCategoryRepository categoryRepository;
    @Autowired private TransmissionTypeRepository transmissionRepository;
    @Autowired private FuelTypeRepository fuelRepository;
    @Autowired private VehicleStatusRepository vehicleStatusRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private InsuranceCoverageRepository insuranceRepository;
    @Autowired private MileagePlanRepository mileagePlanRepository;
    @Autowired private ReservationStatusRepository reservationStatusRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationExpirationService reservationExpirationService;

    private User customer;
    private Vehicle vehicle;
    private Branch branch;
    private InsuranceCoverage insurance;
    private MileagePlan mileagePlan;
    private ReservationStatus pendingStatus;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();

        customer = userRepository.saveAndFlush(new User("Carlos", "Gomez", "customer@drivique.com", "$2a$10$hash"));

        Department department = departmentRepository.saveAndFlush(new Department("Antioquia"));
        City city = cityRepository.saveAndFlush(new City(department, "Medellín", true, true));
        branch = branchRepository.saveAndFlush(new Branch(
                "Sede Poblado",
                "Cra 43A # 1-50",
                city,
                "3001234567",
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                true
        ));

        VehicleBrand brand = brandRepository.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categoryRepository.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("200000"), new BigDecimal("1000000")));
        TransmissionType transmission = transmissionRepository.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuelRepository.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus status = vehicleStatusRepository.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));

        vehicle = vehicleRepository.saveAndFlush(new Vehicle(
                "EXP789",
                "1HGCR2F83HA000888",
                brand,
                category,
                transmission,
                fuel,
                status,
                branch,
                "RAV4",
                (short) 2024,
                "Silver",
                (short) 5,
                (short) 5,
                500,
                15000,
                new BigDecimal("220000.00"),
                null,
                true
        ));

        insurance = insuranceRepository.saveAndFlush(new InsuranceCoverage(
                "Cobertura Básica",
                new BigDecimal("35000.00"),
                "Protección básica"
        ));
        mileagePlan = mileagePlanRepository.saveAndFlush(new MileagePlan(
                "200 km por día",
                200,
                new BigDecimal("15000.00"),
                new BigDecimal("500.00")
        ));

        pendingStatus = reservationStatusRepository.saveAndFlush(new ReservationStatus("PENDING_PAYMENT", "Pending payment", true));
    }

    @Test
    void expirePendingReservations_CancelsOnlyPastExpiredReservationsAndFreesVehicle() {
        Instant now = Instant.now();
        Instant pickupDate1 = now.plus(4, ChronoUnit.DAYS);
        Instant returnDate1 = now.plus(7, ChronoUnit.DAYS);

        // Reservation 1: expired 2 hours ago
        Reservation expiredRes = new Reservation(
                "RES-2026-EXP1",
                customer,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                pickupDate1,
                returnDate1,
                new BigDecimal("220000"),
                new BigDecimal("810000"),
                "CASH-EXP001",
                now.minus(2, ChronoUnit.HOURS),
                true
        );
        reservationRepository.saveAndFlush(expiredRes);

        // Reservation 2: expires in 48 hours
        Instant pickupDate2 = now.plus(10, ChronoUnit.DAYS);
        Instant returnDate2 = now.plus(13, ChronoUnit.DAYS);
        Reservation validRes = new Reservation(
                "RES-2026-VAL1",
                customer,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                pickupDate2,
                returnDate2,
                new BigDecimal("220000"),
                new BigDecimal("810000"),
                "CASH-VAL001",
                now.plus(48, ChronoUnit.HOURS),
                true
        );
        reservationRepository.saveAndFlush(validRes);

        // Verify vehicle is initially blocked for pickupDate1..returnDate1
        boolean initiallyBlocked = reservationRepository.existsOverlappingReservation(vehicle.getId(), pickupDate1, returnDate1);
        assertThat(initiallyBlocked).isTrue();

        // Run expiration service
        var summary = reservationExpirationService.expirePendingReservations();

        // Verify summary
        assertThat(summary.expiredCount()).isEqualTo(1);
        assertThat(summary.expiredReservationCodes()).containsExactly("RES-2026-EXP1");

        // Verify database state for expired reservation
        Reservation updatedExpired = reservationRepository.findByCode("RES-2026-EXP1").orElseThrow();
        assertThat(updatedExpired.getStatus().getCode()).isEqualTo("CANCELLED_BY_TIMEOUT");
        assertThat(updatedExpired.isBlocksAvailability()).isFalse();

        // Verify database state for valid reservation
        Reservation updatedValid = reservationRepository.findByCode("RES-2026-VAL1").orElseThrow();
        assertThat(updatedValid.getStatus().getCode()).isEqualTo("PENDING_PAYMENT");
        assertThat(updatedValid.isBlocksAvailability()).isTrue();

        // Verify vehicle is now FREE for pickupDate1..returnDate1
        boolean blockedAfterExpiration = reservationRepository.existsOverlappingReservation(vehicle.getId(), pickupDate1, pickupDate1.plus(1, ChronoUnit.DAYS));
        assertThat(blockedAfterExpiration).isFalse();

        // But still blocked for pickupDate2..returnDate2
        boolean stillBlockedForValid = reservationRepository.existsOverlappingReservation(vehicle.getId(), pickupDate2, pickupDate2.plus(1, ChronoUnit.DAYS));
        assertThat(stillBlockedForValid).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void triggerExpirePendingEndpoint_WithAdminRole_ExecutesSuccessfully() throws Exception {
        Instant now = Instant.now();
        Reservation expiredRes = new Reservation(
                "RES-2026-ADM1",
                customer,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                now.plus(3, ChronoUnit.DAYS),
                now.plus(5, ChronoUnit.DAYS),
                new BigDecimal("220000"),
                new BigDecimal("540000"),
                "CASH-ADM001",
                now.minus(1, ChronoUnit.HOURS),
                true
        );
        reservationRepository.saveAndFlush(expiredRes);

        mvc.perform(post("/v1/admin/reservations/expire-pending")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredCount", is(1)))
                .andExpect(jsonPath("$.expiredReservationCodes", hasItem("RES-2026-ADM1")))
                .andExpect(jsonPath("$.executedAt", notNullValue()));

        Reservation updated = reservationRepository.findByCode("RES-2026-ADM1").orElseThrow();
        assertThat(updated.getStatus().getCode()).isEqualTo("CANCELLED_BY_TIMEOUT");
        assertThat(updated.isBlocksAvailability()).isFalse();
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void triggerExpirePendingEndpoint_WithCustomerRole_Forbidden() throws Exception {
        mvc.perform(post("/v1/admin/reservations/expire-pending")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void triggerExpirePendingEndpoint_WithoutAuth_Unauthorized() throws Exception {
        mvc.perform(post("/v1/admin/reservations/expire-pending")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
