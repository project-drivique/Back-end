package com.drivique.api;

import com.drivique.api.dto.CreateReservationRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ReservationIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID vehicleId;
    private UUID categoryId;
    private UUID brandId;
    private UUID transmissionId;
    private UUID fuelId;
    private UUID vehicleStatusId;
    private UUID branchId;
    private UUID insuranceId;
    private UUID mileagePlanId;
    private UUID additionalServiceId;
    private UUID promotionId;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();

        // 1. Seed Customer User
        customerId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO iam.users (id, email, password_hash, first_name, last_name, phone_number, is_active, email_verified, created_at, updated_at) " +
                "VALUES (?, 'customer@drivique.com', '$2a$10$hash', 'Carlos', 'Gomez', '+573001112233', true, true, now(), now())",
                customerId
        );

        // 2. Seed Location
        UUID deptId = UUID.randomUUID();
        jdbc.update("INSERT INTO location.departments (id, name, code) VALUES (?, 'Antioquia', 'ANT')", deptId);
        UUID cityId = UUID.randomUUID();
        jdbc.update("INSERT INTO location.cities (id, department_id, name, code) VALUES (?, ?, 'Medellin', 'MED')", cityId, deptId);
        branchId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO location.branches (id, city_id, name, address, phone, is_active, created_at, updated_at) " +
                "VALUES (?, ?, 'Sede El Poblado', 'Cra 43A # 1-50', '3001234567', true, now(), now())",
                branchId, cityId
        );

        // 3. Seed Fleet Requirements
        brandId = UUID.randomUUID();
        jdbc.update("INSERT INTO fleet.vehicle_brands (id, name) VALUES (?, 'Toyota')", brandId);

        categoryId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO fleet.vehicle_categories (id, name, code, security_deposit, is_active) VALUES (?, 'SUV Premium', 'SUV_PREM', 1500000, true)",
                categoryId
        );

        transmissionId = UUID.randomUUID();
        jdbc.update("INSERT INTO fleet.transmission_types (id, name, code) VALUES (?, 'Automatica', 'AUTOMATIC')", transmissionId);

        fuelId = UUID.randomUUID();
        jdbc.update("INSERT INTO fleet.fuel_types (id, name, code) VALUES (?, 'Gasolina', 'GASOLINE')", fuelId);

        vehicleStatusId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO fleet.vehicle_statuses (id, name, code, allows_reservation, is_active) VALUES (?, 'Disponible', 'AVAILABLE', true, true)",
                vehicleStatusId
        );

        vehicleId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO fleet.vehicles (id, plate, vin, brand_id, category_id, transmission_type_id, fuel_type_id, status_id, current_branch_id, model, year, color, passenger_capacity, mileage, daily_rate, is_featured, is_active) " +
                "VALUES (?, 'DRV777', '1HGCR2F83HA000777', ?, ?, ?, ?, ?, ?, 'Corolla Cross', 2024, 'Blanco', 5, 12000, 220000.00, true, true)",
                vehicleId, brandId, categoryId, transmissionId, fuelId, vehicleStatusId, branchId
        );

        // 4. Seed Catalogs (Insurance, Mileage, Additional Services, Promotion)
        insuranceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO catalog.insurance_coverages (id, name, daily_rate, description, is_active) VALUES (?, 'Cobertura Total', 45000.00, 'Proteccion completa', true)",
                insuranceId
        );

        mileagePlanId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO catalog.mileage_plans (id, name, daily_rate, included_km_per_day, extra_km_rate, is_active) VALUES (?, 'Ilimitado', 30000.00, NULL, 0.00, true)",
                mileagePlanId
        );

        additionalServiceId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO catalog.additional_services (id, name, daily_rate, is_active) VALUES (?, 'Silla para Bebe', 15000.00, true)",
                additionalServiceId
        );

        promotionId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO catalog.promotions (id, code, offer_type, discount_type, discount_value, starts_at, ends_at, minimum_rental_days, max_uses_limit, current_uses_count, is_active) " +
                "VALUES (?, 'DESC10', 'PROMOTION', 'PERCENTAGE', 10.00, now() - interval '1 day', now() + interval '30 days', 1, 100, 0, true)",
                promotionId
        );

        // 5. Seed Statuses
        jdbc.update("INSERT INTO rental.reservation_statuses (id, code, name, blocks_availability) VALUES (gen_random_uuid(), 'PENDING_PAYMENT', 'Pending payment', true)");
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

        mvc.perform(post("/api/v1/reservations")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code", startsWith("RES-2026-")))
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
                .andExpect(jsonPath("$.promotion.discountApplied").value(66000.00)) // 10% of 660,000 vehicle subtotal
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
        mvc.perform(post("/api/v1/reservations")
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

        mvc.perform(post("/api/v1/reservations")
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

        mvc.perform(post("/api/v1/reservations")
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

        mvc.perform(post("/api/v1/reservations")
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

        String responseBody = mvc.perform(post("/api/v1/reservations")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reservationId = objectMapper.readTree(responseBody).get("id").asText();
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

        mvc.perform(post("/api/v1/reservations")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
