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
class ReservationDeliveryPointIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired private RoleRepository roleRepository;
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

    private UUID reservationId;
    private UUID branchId;
    private UUID cityId;

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

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void configureDeliveryPoints_BranchAndHomeDelivery_SuccessfullyPersistedAndRetrieved() throws Exception {
        DeliveryPointRequestDTO pickup = new DeliveryPointRequestDTO(
                "PICKUP",
                "BRANCH",
                branchId,
                null,
                null,
                null,
                null,
                "Recoger en sala de espera"
        );

        DeliveryPointRequestDTO returnPoint = new DeliveryPointRequestDTO(
                "RETURN",
                "HOME_DELIVERY",
                null,
                cityId,
                "El Poblado",
                "Calle 10 # 43E-20",
                null,
                "Entregar en la portería del edificio"
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(pickup, returnPoint));

        // 1. Configure delivery points (POST)
        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].pointType").value("PICKUP"))
                .andExpect(jsonPath("$[0].modality").value("BRANCH"))
                .andExpect(jsonPath("$[0].branchId").value(branchId.toString()))
                .andExpect(jsonPath("$[0].branchName").value("Sede El Poblado"))
                .andExpect(jsonPath("$[1].pointType").value("RETURN"))
                .andExpect(jsonPath("$[1].modality").value("HOME_DELIVERY"))
                .andExpect(jsonPath("$[1].cityName").value("Medellín"))
                .andExpect(jsonPath("$[1].neighborhood").value("El Poblado"))
                .andExpect(jsonPath("$[1].address").value("Calle 10 # 43E-20"));

        // 2. Query delivery points (GET)
        mvc.perform(get("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void updateSingleDeliveryPoint_AirportAndTerminal_Success() throws Exception {
        DeliveryPointRequestDTO airportDto = new DeliveryPointRequestDTO(
                "PICKUP",
                "AIRPORT",
                null,
                cityId,
                null,
                null,
                "AV9301",
                "Puerta 4 llegadas nacionales"
        );

        mvc.perform(put("/api/v1/reservations/{id}/delivery-points/{pointType}", reservationId, "PICKUP")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(airportDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointType").value("PICKUP"))
                .andExpect(jsonPath("$.modality").value("AIRPORT"))
                .andExpect(jsonPath("$.flightOrBusNumber").value("AV9301"))
                .andExpect(jsonPath("$.referenceDetails").value("Puerta 4 llegadas nacionales"));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void configureDeliveryPoints_HomeDeliveryMissingAddress_Returns400() throws Exception {
        DeliveryPointRequestDTO invalidHome = new DeliveryPointRequestDTO(
                "RETURN",
                "HOME_DELIVERY",
                null,
                cityId,
                "Laureles",
                "",
                null,
                null
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(invalidHome));

        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "other@drivique.com")
    void configureDeliveryPoints_OtherUser_Returns403() throws Exception {
        DeliveryPointRequestDTO pickup = new DeliveryPointRequestDTO(
                "PICKUP",
                "BRANCH",
                branchId,
                null,
                null,
                null,
                null,
                null
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(pickup));

        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "ADMIN")
    void configureDeliveryPoints_AdminStaff_AllowsConfiguration() throws Exception {
        DeliveryPointRequestDTO terminalDto = new DeliveryPointRequestDTO(
                "PICKUP",
                "TERMINAL",
                null,
                cityId,
                null,
                null,
                "BOLIV-200",
                "Bahía 5"
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(terminalDto));

        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].modality").value("TERMINAL"))
                .andExpect(jsonPath("$[0].flightOrBusNumber").value("BOLIV-200"));
    }

    @Autowired private com.drivique.api.repository.BranchUserRepository branchUserRepository;

    private void configureHomeReturn() throws Exception {
        DeliveryPointRequestDTO pickup = new DeliveryPointRequestDTO("PICKUP", "BRANCH", branchId, null, null, null, null, null);
        DeliveryPointRequestDTO returnPoint = new DeliveryPointRequestDTO("RETURN", "HOME_DELIVERY", null, cityId,
                "El Poblado", "Calle 10 # 43E-20", null, "Portería");
        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(pickup, returnPoint));
        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("customer@drivique.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private void createStaff(String email, UUID forBranchId) {
        Role role = roleRepository.saveAndFlush(roleRepository.findByCode("BRANCH_ADMIN").orElseGet(() -> new Role("BRANCH_ADMIN", "BRANCH_ADMIN", "Branch manager", true)));
        User staff = new User("Laura", "Encargada", email, "$2a$10$hash");
        staff.setRoles(Set.of(role));
        staff = userRepository.saveAndFlush(staff);
        branchUserRepository.saveAndFlush(new com.drivique.api.model.BranchUser(staff, branchRepository.findById(forBranchId).orElseThrow()));
    }

    private static final String DRIVER = """
            {"fullName": "Carlos Ramírez", "documentType": "CC", "documentNumber": "1018432910",
             "email": "carlos.conductor@drivique.com", "phone": "3001112233", "licenseCategory": "C1", "vehicleDescription": "Moto"}
            """;

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void staffManagesDriversAndAssignsThemToHomeDelivery() throws Exception {
        configureHomeReturn();
        createStaff("staff@drivique.com", branchId);

        String created = mvc.perform(post("/api/v1/admin/delivery-drivers").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(DRIVER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.branchName").value("Sede El Poblado"))
                .andReturn().getResponse().getContentAsString();
        String driverId = objectMapper.readTree(created).get("id").asText();

        mvc.perform(post("/api/v1/admin/delivery-drivers").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(DRIVER))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.driverName").value("Carlos Ramírez"))
                .andExpect(jsonPath("$.deliveryPin").doesNotExist());

        // La entrega por sucursal no admite conductor
        mvc.perform(post("/api/v1/admin/deliveries/{id}/PICKUP/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isConflict());

        String pin = jdbc.queryForObject("SELECT delivery_pin FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class);

        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/complete", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\": \"" + ("0000".equals(pin) ? "1111" : "0000") + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/complete", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\": \"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT status FROM location.delivery_drivers", String.class)).isEqualTo("AVAILABLE");

        mvc.perform(delete("/api/v1/admin/delivery-drivers/{id}", driverId).contextPath("/api"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void onlyTheCustomerSeesTheDeliveryPin() throws Exception {
        configureHomeReturn();
        createStaff("staff@drivique.com", branchId);
        jdbc.execute("INSERT INTO location.delivery_drivers (id, branch_id, full_name, document_type, document_number, status, created_at, updated_at) "
                + "SELECT gen_random_uuid(), '" + branchId + "', 'Conductor', 'CC', '123', 'AVAILABLE', now(), now()");
        jdbc.execute("UPDATE rental.reservation_delivery_points SET delivery_pin = '4821', delivery_status = 'ASSIGNED', "
                + "driver_id = (SELECT id FROM location.delivery_drivers) WHERE point_type = 'RETURN'");

        mvc.perform(get("/api/v1/reservations/{id}/delivery-points", reservationId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pointType=='RETURN')].deliveryPin").value("4821"));
    }

    @Test
    @WithMockUser(username = "otherstaff@drivique.com", roles = "BRANCH_ADMIN")
    void staffFromAnotherBranchCannotUseThisReservation() throws Exception {
        configureHomeReturn();
        Branch otherBranch = branchRepository.saveAndFlush(new Branch("Sede Laureles", "Cra 70 # 1-1",
                cityRepository.findById(cityId).orElseThrow(), "3009999999", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        createStaff("otherstaff@drivique.com", otherBranch.getId());
        jdbc.execute("INSERT INTO location.delivery_drivers (id, branch_id, full_name, document_type, document_number, status, created_at, updated_at) "
                + "SELECT gen_random_uuid(), '" + otherBranch.getId() + "', 'Conductor', 'CC', '999', 'AVAILABLE', now(), now()");
        String driverId = jdbc.queryForObject("SELECT id::text FROM location.delivery_drivers", String.class);

        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Autowired private com.drivique.api.repository.BranchReviewRepository branchReviewRepository;

    private UUID createReview() {
        User customer = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("customer@drivique.com").orElseThrow();
        com.drivique.api.model.BranchReview review = new com.drivique.api.model.BranchReview(
                branchRepository.findById(branchId).orElseThrow(), customer,
                reservationRepository.findById(reservationId).orElseThrow(), (short) 4, "Buena atención");
        return branchReviewRepository.saveAndFlush(review).getId();
    }

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void staffListsRepliesAndDeletesBranchReviews() throws Exception {
        createStaff("staff@drivique.com", branchId);
        UUID reviewId = createReview();

        mvc.perform(get("/api/v1/admin/reviews/branches").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Carlos Gomez"))
                .andExpect(jsonPath("$[0].reservationCode").value("RES-2026-9999"))
                .andExpect(jsonPath("$[0].managerReply").doesNotExist());

        mvc.perform(patch("/api/v1/admin/reviews/branches/{id}/reply", reviewId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\": \"Gracias por tu opinión\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerReply").value("Gracias por tu opinión"));

        mvc.perform(patch("/api/v1/admin/reviews/branches/{id}/reply", reviewId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\": \"  \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/admin/reviews/branches/{id}", reviewId).contextPath("/api"))
                .andExpect(status().isNoContent());
        assertThat(branchReviewRepository.count()).isZero();
    }

    @Test
    @WithMockUser(username = "otherstaff@drivique.com", roles = "BRANCH_ADMIN")
    void staffFromAnotherBranchCannotSeeOrAnswerThisReview() throws Exception {
        UUID reviewId = createReview();
        Branch otherBranch = branchRepository.saveAndFlush(new Branch("Sede Laureles", "Cra 70 # 1-1",
                cityRepository.findById(cityId).orElseThrow(), "3009999999", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        createStaff("otherstaff@drivique.com", otherBranch.getId());

        mvc.perform(get("/api/v1/admin/reviews/branches").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(patch("/api/v1/admin/reviews/branches/{id}/reply", reviewId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\": \"Hola\"}"))
                .andExpect(status().isForbidden());
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.drivique.api.service.DeliveryActionTokens actionTokens;

    private String assignDriverAndGetToken(String action) throws Exception {
        configureHomeReturn();
        createStaff("staff@drivique.com", branchId);
        String created = mvc.perform(post("/api/v1/admin/delivery-drivers").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(DRIVER)).andReturn().getResponse().getContentAsString();
        String driverId = objectMapper.readTree(created).get("id").asText();
        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isOk());
        return actionTokens.create(java.util.UUID.fromString(String.valueOf(reservationId)), "RETURN", action, java.time.Instant.now().plusSeconds(3600));
    }

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void driverConfirmsFromTheEmailLinkAndEveryoneIsNotified() throws Exception {
        String token = assignDriverAndGetToken("CONFIRM");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject = 'Tu conductor ya fue asignado' AND message LIKE '%PIN%'", Integer.class)).isEqualTo(2);

        // abrir el enlace solo muestra la página: no confirma nada por sí solo
        mvc.perform(get("/api/v1/public/deliveries/action").contextPath("/api").param("token", token))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class)).isEqualTo("ASSIGNED");

        mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM location.delivery_drivers", String.class)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject = 'Entrega confirmada'", Integer.class)).isGreaterThan(0);

        // el enlace ya no sirve para volver a confirmar
        mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void driverRejectsTheCodeAndTheDeliveryStaysPending() throws Exception {
        String token = assignDriverAndGetToken("REJECT");
        mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class)).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject = 'Código de entrega rechazado'", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject = 'Revisa tu código de entrega'", Integer.class)).isGreaterThan(0);
    }

    @Test
    void tamperedOrExpiredLinksAreRefused() throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        String expired = actionTokens.create(id, "RETURN", "CONFIRM", java.time.Instant.now().minusSeconds(10));
        String tampered = actionTokens.create(id, "RETURN", "CONFIRM", java.time.Instant.now().plusSeconds(600)) + "x";
        for (String token : java.util.List.of(expired, tampered, "basura")) {
            mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                    .andExpect(status().isGone());
        }
    }
}
