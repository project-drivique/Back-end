package com.drivique.api.reservations;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.dto.ConfigureDeliveryPointsRequestDTO;
import com.drivique.api.dto.DeliveryPointRequestDTO;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
abstract class ReservationDeliveryTestBase extends DatabaseHealthTestSupport {

    @Autowired protected MockMvc mvc;

    protected final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired protected RoleRepository roleRepository;

    @Autowired protected UserRepository userRepository;

    @Autowired protected DepartmentRepository departmentRepository;

    @Autowired protected CityRepository cityRepository;

    @Autowired protected BranchRepository branchRepository;

    @Autowired protected VehicleBrandRepository brandRepository;

    @Autowired protected VehicleCategoryRepository categoryRepository;

    @Autowired protected TransmissionTypeRepository transmissionRepository;

    @Autowired protected FuelTypeRepository fuelRepository;

    @Autowired protected VehicleStatusRepository vehicleStatusRepository;

    @Autowired protected VehicleRepository vehicleRepository;

    @Autowired protected InsuranceCoverageRepository insuranceRepository;

    @Autowired protected MileagePlanRepository mileagePlanRepository;

    @Autowired protected ReservationStatusRepository reservationStatusRepository;

    @Autowired protected ReservationRepository reservationRepository;

    protected UUID reservationId;

    protected UUID branchId;

    protected UUID cityId;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();

        // 1. Users & Roles
        User customer = new User("Carlos", "Gomez", "customer@drivique.com", "$2a$10$hash");
        userRepository.saveAndFlush(customer);

        User otherCustomer = new User("Lucia", "Perez", "other@drivique.com", "$2a$10$hash");
        userRepository.saveAndFlush(otherCustomer);

        Role adminRole = roleRepository.saveAndFlush(new Role("ADMIN", "ADMIN", "Administrator", true));
        User adminUser = new User("Admin", "Staff", "admin@drivique.com", "$2a$10$hash");
        adminUser.setRoles(Set.of(adminRole));
        userRepository.saveAndFlush(adminUser);

        // 2. Location
        Department department = departmentRepository.saveAndFlush(new Department("Antioquia"));
        City city = cityRepository.saveAndFlush(new City(department, "Medellín", true, true));
        cityId = city.getId();

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
                "DRV888",
                "1HGCR2F83HA000888",
                brand,
                category,
                transmission,
                fuel,
                status,
                branch,
                "Corolla Cross",
                (short) 2024,
                "Gris",
                (short) 5,
                (short) 5,
                500,
                10000,
                new BigDecimal("220000.00"),
                null,
                true
        ));

        // 4. Catalogs
        InsuranceCoverage insurance = insuranceRepository.saveAndFlush(new InsuranceCoverage(
                "Cobertura Basica",
                new BigDecimal("25000.00"),
                "Deducible estandar"
        ));

        MileagePlan mileagePlan = mileagePlanRepository.saveAndFlush(new MileagePlan(
                "Estandar 200km",
                200,
                BigDecimal.ZERO,
                new BigDecimal("1000.00")
        ));

        ReservationStatus pendingStatus = reservationStatusRepository.saveAndFlush(
                new ReservationStatus("PENDING_PAYMENT", "Pending payment", true)
        );

        // 5. Reservation
        Instant pickup = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(4, ChronoUnit.DAYS);

        Reservation res = new Reservation(
                "RES-2026-9999",
                customer,
                vehicle,
                pendingStatus,
                insurance,
                mileagePlan,
                branch,
                pickup,
                returnDate,
                new BigDecimal("220000.00"),
                new BigDecimal("880000.00"),
                null,
                null,
                true
        );
        Reservation savedRes = reservationRepository.saveAndFlush(res);
        reservationId = savedRes.getId();
    }

    @Autowired protected com.drivique.api.repository.BranchUserRepository branchUserRepository;

    protected void configureHomeReturn() throws Exception {
        DeliveryPointRequestDTO pickup = new DeliveryPointRequestDTO("PICKUP", "BRANCH", branchId, null, null, null, null, null);
        DeliveryPointRequestDTO returnPoint = new DeliveryPointRequestDTO("RETURN", "HOME_DELIVERY", null, cityId,
                "El Poblado", "Calle 10 # 43E-20", null, "Portería");
        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(pickup, returnPoint));
        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("customer@drivique.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    protected void createStaff(String email, UUID forBranchId) {
        Role role = roleRepository.saveAndFlush(roleRepository.findByCode("BRANCH_ADMIN").orElseGet(() -> new Role("BRANCH_ADMIN", "BRANCH_ADMIN", "Branch manager", true)));
        User staff = new User("Laura", "Encargada", email, "$2a$10$hash");
        staff.setRoles(Set.of(role));
        staff = userRepository.saveAndFlush(staff);
        branchUserRepository.saveAndFlush(new com.drivique.api.model.BranchUser(staff, branchRepository.findById(forBranchId).orElseThrow()));
    }

    protected static final String DRIVER = """
            {"fullName": "Carlos Ramírez", "documentType": "CC", "documentNumber": "1018432910",
             "email": "carlos.conductor@drivique.com", "phone": "3001112233", "licenseCategory": "C1", "vehicleDescription": "Moto"}
            """;

    @Autowired protected com.drivique.api.repository.BranchReviewRepository branchReviewRepository;

    protected UUID createReview() {
        User customer = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("customer@drivique.com").orElseThrow();
        com.drivique.api.model.BranchReview review = new com.drivique.api.model.BranchReview(
                branchRepository.findById(branchId).orElseThrow(), customer,
                reservationRepository.findById(reservationId).orElseThrow(), (short) 4, "Buena atención");
        return branchReviewRepository.saveAndFlush(review).getId();
    }

    @org.springframework.beans.factory.annotation.Autowired
    protected com.drivique.api.service.DeliveryActionTokens actionTokens;

    protected String assignDriverAndGetToken(String action) throws Exception {
        configureHomeReturn();
        createStaff("staff@drivique.com", branchId);
        String created = mvc.perform(post("/api/v1/admin/delivery-drivers").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(DRIVER)).andReturn().getResponse().getContentAsString();
        String driverId = objectMapper.readTree(created).get("id").asText();
        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isOk());
        jdbc.execute("UPDATE rental.reservations SET pickup_date = now() - interval '2 days', return_date = now() WHERE id = '" + reservationId + "'");
        return actionTokens.create(java.util.UUID.fromString(String.valueOf(reservationId)), "RETURN", action, java.time.Instant.now().plusSeconds(3600));
    }
}
