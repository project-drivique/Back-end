package com.drivique.api.reservations;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.dto.CreateReservationRequestDTO;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ReservationIntegrationTests extends DatabaseHealthTestSupport {

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
    @Autowired private AdditionalServiceRepository additionalServiceRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private ReservationStatusRepository reservationStatusRepository;

    private UUID vehicleId;
    private UUID insuranceId;
    private UUID mileagePlanId;
    private UUID additionalServiceId;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();

        // 1. Customer User
        User customer = new User("Carlos", "Gomez", "customer@drivique.com", "$2a$10$hash");
        userRepository.saveAndFlush(customer);

        // 2. Location
        Department department = departmentRepository.saveAndFlush(new Department("Antioquia"));
        City city = cityRepository.saveAndFlush(new City(department, "Medellín", true, true));
        Branch branch = branchRepository.saveAndFlush(new Branch(
                "Sede El Poblado",
                "Cra 43A # 1-50",
                city,
                "3001234567",
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                true
        ));
        branchId = branch.getId();

        // 3. Fleet
        VehicleBrand brand = brandRepository.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categoryRepository.saveAndFlush(new VehicleCategory(
                "SUV Premium",
                new BigDecimal("220000"),
                new BigDecimal("1500000")
        ));
        TransmissionType transmission = transmissionRepository.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuelRepository.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus status = vehicleStatusRepository.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));

        Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle(
                "DRV777",
                "1HGCR2F83HA000777",
                brand,
                category,
                transmission,
                fuel,
                status,
                branch,
                "Corolla Cross",
                (short) 2024,
                "Blanco",
                (short) 5,
                (short) 5,
                500,
                12000,
                new BigDecimal("220000.00"),
                null,
                true
        ));
        vehicleId = vehicle.getId();

        // 4. Catalogs
        InsuranceCoverage insurance = insuranceRepository.saveAndFlush(new InsuranceCoverage(
                "Cobertura Total",
                new BigDecimal("45000.00"),
                "Proteccion completa"
        ));
        insuranceId = insurance.getId();

        MileagePlan mileagePlan = mileagePlanRepository.saveAndFlush(new MileagePlan(
                "Ilimitado",
                null,
                new BigDecimal("30000.00"),
                BigDecimal.ZERO
        ));
        mileagePlanId = mileagePlan.getId();

        AdditionalService additionalService = additionalServiceRepository.saveAndFlush(new AdditionalService(
                "Silla para Bebe",
                new BigDecimal("15000.00")
        ));
        additionalServiceId = additionalService.getId();


        promotionRepository.saveAndFlush(new Promotion(
                "DESC10",
                "PROMOTION",
                "PERCENTAGE",
                new BigDecimal("10.00"),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS),
                1,
                100,
                0
        ));

        // 5. Reservation Status
        reservationStatusRepository.saveAndFlush(new ReservationStatus("PENDING_PAYMENT", "Pending payment", true));
        reservationStatusRepository.saveAndFlush(new ReservationStatus("CONFIRMED", "Confirmed", true));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void createsReservationSuccessfullyWithPricingAndLocks() throws Exception {
        Instant pickup = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS); // 3 days

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

        mvc.perform(post("/api/v1/reservations/checkout/initiate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code", startsWith("HLD-")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.rentalDays").value(3))
                .andExpect(jsonPath("$.vehicleDailyRate").value(220000.00))
                .andExpect(jsonPath("$.vehicleSubtotal").value(660000.00))
                .andExpect(jsonPath("$.insuranceDailyRate").value(45000.00))
                .andExpect(jsonPath("$.mileagePlanDailyRate").value(30000.00))
                .andExpect(jsonPath("$.additionalServices", hasSize(1)))
                .andExpect(jsonPath("$.additionalServices[0].name").value("Silla para Bebe"))
                .andExpect(jsonPath("$.additionalServices[0].dailyRate").value(15000.00))
                .andExpect(jsonPath("$.promotion.code").value("DESC10"))
                .andExpect(jsonPath("$.promotion.discountApplied").value(66000.00)) // 10% of 660k
                // Total = 660,000 + 135,000 (insurance) + 90,000 (mileage) + 45,000 (baby seat) - 66,000 (discount) = 864,000
                .andExpect(jsonPath("$.totalEstimated").value(864000.00))
                .andExpect(jsonPath("$.cashPaymentCode", startsWith("CASH-")))
                .andExpect(jsonPath("$.cashPaymentExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.blocksAvailability").value(true));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void preventsOverbookingOnOverlappingDates() throws Exception {
        Instant pickup = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(4, ChronoUnit.DAYS);

        CreateReservationRequestDTO request1 = new CreateReservationRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        // First reservation succeeds
        mvc.perform(post("/api/v1/reservations/checkout/initiate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Overlapping reservation for the same vehicle (pickup in between)
        CreateReservationRequestDTO overlappingRequest = new CreateReservationRequestDTO(
                vehicleId,
                pickup.plus(1, ChronoUnit.DAYS),
                returnDate.plus(2, ChronoUnit.DAYS),
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        mvc.perform(post("/api/v1/reservations/checkout/initiate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overlappingRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void allowsNonOverlappingSubsequentReservation() throws Exception {
        Instant pickup1 = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant return1 = pickup1.plus(2, ChronoUnit.DAYS);

        CreateReservationRequestDTO request1 = new CreateReservationRequestDTO(
                vehicleId,
                pickup1,
                return1,
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        mvc.perform(post("/api/v1/reservations/checkout/initiate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Subsequent non-overlapping reservation (starts exactly at or after return1)
        Instant pickup2 = return1.plus(1, ChronoUnit.DAYS);
        Instant return2 = pickup2.plus(3, ChronoUnit.DAYS);

        CreateReservationRequestDTO request2 = new CreateReservationRequestDTO(
                vehicleId,
                pickup2,
                return2,
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        mvc.perform(post("/api/v1/reservations/checkout/initiate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void retrievesUserReservationsAndDetailByIdAndCode() throws Exception {
        Instant pickup = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(2, ChronoUnit.DAYS);

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

        String responseBody = mvc.perform(post("/api/v1/reservations/checkout/initiate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reservationId = objectMapper.readTree(responseBody).get("id").asText();

        // Confirm it to see it in /me
        responseBody = mvc.perform(post("/api/v1/reservations/checkout/confirm/{id}", reservationId).contextPath("/api"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        
        String code = objectMapper.readTree(responseBody).get("code").asText();

        // 1. Get my reservations
        mvc.perform(get("/api/v1/reservations/me").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(reservationId))
                .andExpect(jsonPath("$[0].code").value(code));

        // 2. Get by ID
        mvc.perform(get("/api/v1/reservations/{id}", reservationId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.customerEmail").value("customer@drivique.com"));

        // 3. Get by Code
        mvc.perform(get("/api/v1/reservations/code/{code}", code).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void unauthenticatedUserCannotCreateReservation() throws Exception {
        CreateReservationRequestDTO request = new CreateReservationRequestDTO(
                vehicleId,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS),
                insuranceId,
                mileagePlanId,
                null,
                null,
                null
        );

        mvc.perform(post("/api/v1/reservations/checkout/initiate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
