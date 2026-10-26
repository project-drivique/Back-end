package com.drivique.api.reservations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.dto.CreateReservationRequestDTO;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.IdempotencyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Protocolo Idempotency-Key sobre POST /v1/reservations/checkout/initiate: la repetición devuelve lo mismo y no vuelve a ejecutar. */
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class IdempotencyKeyIntegrationTests extends DatabaseHealthTestSupport {

    private static final String URL = "/api/v1/reservations/checkout/initiate";

    @Autowired private MockMvc mvc;
    @Autowired private IdempotencyService idempotency;
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
    @Autowired private DocumentStatusRepository documentStatusRepository;
    @Autowired private DocumentTypeRepository documentTypeRepository;
    @Autowired private UserDocumentRepository userDocumentRepository;

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private UUID vehicleId;
    private UUID insuranceId;
    private UUID mileagePlanId;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();
        jdbc.execute("DELETE FROM support.idempotency_keys");

        DocumentStatus approved = documentStatusRepository.saveAndFlush(new DocumentStatus("APPROVED", "Aprobado", "Aprobado"));
        DocumentType identity = documentTypeRepository.saveAndFlush(new DocumentType("CC", "Cédula de Ciudadanía", "Documento de identidad", true, true, true));
        DocumentType license = documentTypeRepository.saveAndFlush(new DocumentType("DRIVER_LICENSE", "Licencia de conducción", "Licencia", true, true, true));
        for (int i = 0; i < 2; i++) {
            User user = new User("Cliente", "N" + i, "cliente" + i + "@drivique.com", "$2a$10$hash");
            user.setProfileComplete(true);
            userRepository.saveAndFlush(user);
            userDocumentRepository.saveAndFlush(new UserDocument(user, identity, approved, "1000" + i, "front.jpg", "back.jpg"));
            userDocumentRepository.saveAndFlush(new UserDocument(user, license, approved, "DL" + i, "front.jpg", "back.jpg"));
        }

        Department department = departmentRepository.saveAndFlush(new Department("Antioquia"));
        City city = cityRepository.saveAndFlush(new City(department, "Medellín", true, true));
        Branch branch = branchRepository.saveAndFlush(new Branch(
                "Sede El Poblado", "Cra 43A # 1-50", city, "3001234567", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        branchId = branch.getId();
        VehicleBrand brand = brandRepository.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categoryRepository.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("220000"), new BigDecimal("1500000")));
        TransmissionType transmission = transmissionRepository.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuelRepository.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus status = vehicleStatusRepository.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));
        vehicleId = vehicleRepository.saveAndFlush(new Vehicle(
                "DRV777", "1HGCR2F83HA000777", brand, category, transmission, fuel, status, branch,
                "Corolla Cross", (short) 2024, "Blanco", (short) 5, (short) 5, 500, 12000,
                new BigDecimal("220000.00"), null, true)).getId();
        insuranceId = insuranceRepository.saveAndFlush(new InsuranceCoverage("Cobertura Total", new BigDecimal("45000.00"), "Proteccion completa")).getId();
        mileagePlanId = mileagePlanRepository.saveAndFlush(new MileagePlan("Ilimitado", null, new BigDecimal("30000.00"), BigDecimal.ZERO)).getId();
        reservationStatusRepository.saveAndFlush(new ReservationStatus("PENDING_PAYMENT", "Pending payment", true));
        reservationStatusRepository.saveAndFlush(new ReservationStatus("CANCELLED", "Cancelled", false));
    }

    private String body(int dayOffset) throws Exception {
        Instant pickup = Instant.now().plus(10 + dayOffset, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        return json.writeValueAsString(new CreateReservationRequestDTO(
                vehicleId, pickup, pickup.plus(3, ChronoUnit.DAYS), insuranceId, mileagePlanId, List.of(), null, branchId));
    }

    private MvcResult send(String email, String key, String body) throws Exception {
        var request = post(URL).contextPath("/api").with(user(email).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn();
    }

    private static String bodyOf(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private int reservationCount() {
        return jdbc.queryForObject("SELECT count(*) FROM rental.reservations", Integer.class);
    }

    @Test
    void repeatedRequestWithTheSameKeyReturnsTheStoredResponse_andDoesNotExecuteAgain() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = body(0);

        MvcResult first = send("cliente0@drivique.com", key, body);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
        String reservationId = json.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // La reserva se cancela (el estado cambió): una repetición que SÍ se ejecutara no devolvería lo mismo ni dejaría 1 sola fila
        jdbc.update("UPDATE rental.reservations SET status_id = (SELECT id FROM rental.reservation_statuses WHERE code = 'CANCELLED'), blocks_availability = false");

        MvcResult second = send("cliente0@drivique.com", key, body);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.getResponse().getHeader("Idempotency-Key")).isEqualTo(key);
        assertThat(json.readTree(second.getResponse().getContentAsString()).get("id").asText()).isEqualTo(reservationId);
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(reservationCount()).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentRequestIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        assertThat(send("cliente0@drivique.com", key, body(0)).getResponse().getStatus()).isEqualTo(201);

        MvcResult reused = send("cliente0@drivique.com", key, body(20));

        assertThat(reused.getResponse().getStatus()).isEqualTo(422);
        JsonNode problem = json.readTree(reused.getResponse().getContentAsString());
        assertThat(problem.get("errors").get(0).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(reservationCount()).isEqualTo(1);
    }

    @Test
    void tenSimultaneousRetriesWithTheSameKeyCreateOneReservationAndShareTheSameResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = body(0);
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return send("cliente0@drivique.com", key, body);
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();

        List<MvcResult> results = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(201));
        assertThat(results.stream().map(r -> bodyOf(r)).distinct()).hasSize(1);
        long replays = results.stream().filter(r -> "true".equals(r.getResponse().getHeader("Idempotent-Replayed"))).count();
        System.out.println("=== 10 REINTENTOS SIMULTÁNEOS CON LA MISMA Idempotency-Key: 1 ejecución real + " + replays + " respuestas repetidas ===");
        assertThat(replays).isEqualTo(attempts - 1L);
        assertThat(reservationCount()).isEqualTo(1);
    }

    @Test
    void theKeyIsScopedToThePerson_anotherPersonWithTheSameKeyIsNotAReplay() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = body(0);
        assertThat(send("cliente0@drivique.com", key, body).getResponse().getStatus()).isEqualTo(201);

        MvcResult other = send("cliente1@drivique.com", key, body);

        // Se ejecuta de verdad (y el vehículo ya está retenido por la primera persona): nunca devuelve la reserva ajena
        assertThat(other.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(other.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void requestsWithoutTheHeaderStillWork_andAnInvalidKeyIsRefused() throws Exception {
        assertThat(send("cliente0@drivique.com", null, body(0)).getResponse().getStatus()).isEqualTo(201);

        MvcResult invalid = send("cliente1@drivique.com", "corta", body(30));
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(json.readTree(invalid.getResponse().getContentAsString()).get("errors").get(0).get("code").asText())
                .isEqualTo("IDEMPOTENCY_KEY_INVALID");
    }

    @Test
    void expiredKeysArePurged() throws Exception {
        send("cliente0@drivique.com", UUID.randomUUID().toString(), body(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.idempotency_keys", Integer.class)).isEqualTo(1);

        jdbc.update("UPDATE support.idempotency_keys SET expires_at = now() - interval '1 minute'");
        idempotency.purgeExpired();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.idempotency_keys", Integer.class)).isZero();
    }

    @Test
    void aFailedOperationDoesNotKeepTheKeyForever() throws Exception {
        String key = UUID.randomUUID().toString();
        // Cuerpo inválido -> 400 (se guarda como respuesta determinista); un 5xx se abandona y se puede reintentar
        MvcResult bad = send("cliente0@drivique.com", key, "{}");
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        MvcResult again = send("cliente0@drivique.com", key, "{}");
        assertThat(again.getResponse().getStatus()).isEqualTo(400);
        assertThat(again.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
    }
}
