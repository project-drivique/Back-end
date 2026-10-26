package com.drivique.api.reservations;

import static org.assertj.core.api.Assertions.assertThat;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.dto.CreateReservationRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.ReservationExpirationService;
import com.drivique.api.service.ReservationService;
import com.drivique.api.service.WompiPaymentService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Demuestra que dos o más personas no pueden reservar el mismo vehículo en fechas que se cruzan, aunque lo intenten
 * en el mismo instante. Hay dos capas:
 * 1) Spring/JPA: el vehículo se bloquea con SELECT ... FOR UPDATE (bloqueo pesimista), así que las peticiones se
 *    atienden una a la vez y la segunda ya ve la reserva de la primera.
 * 2) PostgreSQL: una restricción EXCLUDE USING GIST sobre (vehículo, rango de fechas) rechaza el cruce aunque
 *    alguien se salte la aplicación.
 */
@ActiveProfiles("dev")
class ReservationConcurrencyIntegrationTests extends DatabaseHealthTestSupport {

    private static final int PEOPLE = 10;

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationExpirationService expirationService;
    // Wompi se simula como "transacción aprobada"; lo que se prueba aquí es qué reserva queda con el vehículo
    @MockitoBean private WompiPaymentService wompi;
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
    @Autowired private ContractStatusRepository contractStatusRepository;
    @Autowired private DocumentStatusRepository documentStatusRepository;
    @Autowired private DocumentTypeRepository documentTypeRepository;
    @Autowired private UserDocumentRepository userDocumentRepository;

    private UUID vehicleId;
    private UUID insuranceId;
    private UUID mileagePlanId;
    private UUID branchId;
    private final List<String> emails = new ArrayList<>();

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();
        emails.clear();

        // Diez personas distintas (así la idempotencia de "misma persona, misma solicitud" no oculta el resultado)
        for (int i = 0; i < PEOPLE; i++) {
            String email = "cliente" + i + "@drivique.com";
            User user = new User("Cliente", "Numero" + i, email, "$2a$10$hash");
            user.setProfileComplete(true);
            userRepository.saveAndFlush(user);
            emails.add(email);
        }
        // Con identidad y licencia aprobadas por la sucursal la reserva queda lista para pagar
        DocumentStatus approved = documentStatusRepository.saveAndFlush(new DocumentStatus("APPROVED", "Aprobado", "Aprobado"));
        DocumentType identity = documentTypeRepository.saveAndFlush(new DocumentType("CC", "Cédula de Ciudadanía", "Documento de identidad", true, true, true));
        DocumentType license = documentTypeRepository.saveAndFlush(new DocumentType("DRIVER_LICENSE", "Licencia de conducción", "Licencia", true, true, true));
        for (int i = 0; i < PEOPLE; i++) {
            User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(emails.get(i)).orElseThrow();
            userDocumentRepository.saveAndFlush(new UserDocument(user, identity, approved, "1000" + i, "front.jpg", "back.jpg"));
            userDocumentRepository.saveAndFlush(new UserDocument(user, license, approved, "DL" + i, "front.jpg", "back.jpg"));
        }

        Department department = departmentRepository.saveAndFlush(new Department("Antioquia"));
        City city = cityRepository.saveAndFlush(new City(department, "Medellín", true, true));
        Branch branch = branchRepository.saveAndFlush(new Branch(
                "Sede El Poblado", "Cra 43A # 1-50", city, "3001234567", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        branchId = branch.getId();

        VehicleBrand brand = brandRepository.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categoryRepository.saveAndFlush(
                new VehicleCategory("SUV Premium", new BigDecimal("220000"), new BigDecimal("1500000")));
        TransmissionType transmission = transmissionRepository.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuelRepository.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus status = vehicleStatusRepository.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));
        Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle(
                "DRV777", "1HGCR2F83HA000777", brand, category, transmission, fuel, status, branch,
                "Corolla Cross", (short) 2024, "Blanco", (short) 5, (short) 5, 500, 12000,
                new BigDecimal("220000.00"), null, true));
        vehicleId = vehicle.getId();

        insuranceId = insuranceRepository.saveAndFlush(
                new InsuranceCoverage("Cobertura Total", new BigDecimal("45000.00"), "Proteccion completa")).getId();
        mileagePlanId = mileagePlanRepository.saveAndFlush(
                new MileagePlan("Ilimitado", null, new BigDecimal("30000.00"), BigDecimal.ZERO)).getId();

        reservationStatusRepository.saveAndFlush(new ReservationStatus("PENDING_PAYMENT", "Pending payment", true));
        reservationStatusRepository.saveAndFlush(new ReservationStatus("PENDING_DOCUMENT_REVIEW", "Pending review", true));
        reservationStatusRepository.saveAndFlush(new ReservationStatus("PENDING_SIGNATURE", "Pending signature", true));
        reservationStatusRepository.saveAndFlush(new ReservationStatus("CANCELLED_BY_TIMEOUT", "Cancelled by timeout", false));
        contractStatusRepository.saveAndFlush(new ContractStatus("DRAFT", "Draft", true, false));
        contractStatusRepository.saveAndFlush(new ContractStatus("PENDING_SIGNATURE", "Pending signature", true, false));
    }

    private CreateReservationRequestDTO request(Instant pickup, Instant returnDate) {
        return new CreateReservationRequestDTO(vehicleId, pickup, returnDate, insuranceId, mileagePlanId, List.of(), null, branchId);
    }

    private record Outcome(String email, boolean created, String error) {}

    /** Lanza todas las tareas a la vez (una compuerta las libera juntas) y espera sus resultados. */
    private List<Outcome> fireSimultaneously(List<Callable<Outcome>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (Callable<Outcome> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return outcomes;
    }

    private Callable<Outcome> attempt(String email, Instant pickup, Instant returnDate) {
        return () -> {
            try {
                reservationService.createReservation(request(pickup, returnDate), email);
                return new Outcome(email, true, null);
            } catch (ConflictException ex) {
                return new Outcome(email, false, ex.getMessage());
            }
        };
    }

    @Test
    void tenPeopleReservingTheSameVehicleAtTheSameInstant_onlyOneSucceeds() throws Exception {
        Instant pickup = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(3, ChronoUnit.DAYS);

        List<Outcome> outcomes = fireSimultaneously(emails.stream().map(e -> attempt(e, pickup, returnDate)).toList());

        long created = outcomes.stream().filter(Outcome::created).count();
        List<Outcome> rejected = outcomes.stream().filter(o -> !o.created()).toList();
        System.out.println("=== CONCURRENCIA: " + PEOPLE + " personas, mismo vehículo, mismas fechas ===");
        outcomes.forEach(o -> System.out.println("  " + o.email() + " -> " + (o.created() ? "RESERVA CREADA" : "RECHAZADA: " + o.error())));

        assertThat(created).isEqualTo(1);
        assertThat(rejected).hasSize(PEOPLE - 1);
        assertThat(rejected).allSatisfy(o -> assertThat(o.error()).contains("no se encuentra disponible"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental.reservations WHERE vehicle_id = ?::uuid", Integer.class, vehicleId.toString()))
                .isEqualTo(1);
    }

    @Test
    void threeClientsAtTheSameTime_theFirstOneKeepsTheVehicleAndPays_theOthersCannot() throws Exception {
        Instant pickup = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(2, ChronoUnit.DAYS);
        List<String> three = emails.subList(0, 3);

        // 1) Los tres lo intentan en el mismo instante: solo uno obtiene el vehículo
        List<Outcome> outcomes = fireSimultaneously(three.stream().map(e -> attempt(e, pickup, returnDate)).toList());
        List<Outcome> winners = outcomes.stream().filter(Outcome::created).toList();
        List<Outcome> losers = outcomes.stream().filter(o -> !o.created()).toList();
        System.out.println("=== 3 CLIENTES A LA VEZ ===");
        outcomes.forEach(o -> System.out.println("  " + o.email() + " -> " + (o.created() ? "RETIENE EL VEHÍCULO" : "NO PUEDE: " + o.error())));
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(2);

        // 2) Quien lo retiene paga (transacción aprobada) y la reserva queda confirmada a su nombre
        String winnerEmail = winners.get(0).email();
        String holdCode = jdbc.queryForObject("SELECT code FROM rental.reservations WHERE vehicle_id = ?::uuid", String.class, vehicleId.toString());
        reservationService.confirmReservationPayment(holdCode, "tx-aprobada", winnerEmail);
        String status = jdbc.queryForObject("SELECT s.code FROM rental.reservations r JOIN rental.reservation_statuses s ON s.id = r.status_id", String.class);
        System.out.println("  pago aprobado de " + winnerEmail + " -> reserva en estado " + status);
        assertThat(status).isIn("PENDING_SIGNATURE", "CONFIRMED");

        // 3) Los otros dos, aunque insistan después, siguen sin poder: el vehículo ya no está disponible
        for (Outcome loser : losers) {
            Outcome retry = attempt(loser.email(), pickup, returnDate).call();
            assertThat(retry.created()).as(loser.email() + " no debe poder reservar").isFalse();
            assertThat(retry.error()).contains("no se encuentra disponible");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental.reservations", Integer.class)).isEqualTo(1);
    }

    @Test
    void ifTheFirstClientNeverPays_theHoldExpiresAndTheNextClientCanReserve() throws Exception {
        Instant pickup = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant returnDate = pickup.plus(2, ChronoUnit.DAYS);

        assertThat(attempt(emails.get(0), pickup, returnDate).call().created()).isTrue();
        // Mientras la retención siga vigente, el segundo no puede
        assertThat(attempt(emails.get(1), pickup, returnDate).call().created()).isFalse();

        // El primero no paga y vence su plazo: el proceso de expiración libera el vehículo
        jdbc.update("UPDATE rental.reservations SET cash_payment_expires_at = now() - interval '1 hour'");
        expirationService.expirePendingReservations();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental.reservations WHERE blocks_availability", Integer.class)).isZero();
        Outcome second = attempt(emails.get(1), pickup, returnDate).call();
        System.out.println("  tras vencer el plazo sin pago, " + emails.get(1) + " -> " + (second.created() ? "RESERVA CREADA" : second.error()));
        assertThat(second.created()).isTrue();
    }

    @Test
    void partiallyOverlappingRanges_theLockKeepsThemFromCrossing() throws Exception {
        Instant base = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        // Cada persona pide 4 días y arranca 1 día después de la anterior: todos los rangos se cruzan entre sí en cadena
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            Instant pickup = base.plus(i, ChronoUnit.DAYS);
            tasks.add(attempt(emails.get(i), pickup, pickup.plus(4, ChronoUnit.DAYS)));
        }

        List<Outcome> outcomes = fireSimultaneously(tasks);

        // Ninguna pareja de reservas aceptadas puede cruzarse (lo comprueba PostgreSQL con el operador de rangos &&)
        Integer crossings = jdbc.queryForObject(
                "SELECT count(*) FROM rental.reservations a JOIN rental.reservations b ON a.id < b.id AND a.vehicle_id = b.vehicle_id "
                        + "AND tstzrange(a.pickup_date, a.return_date, '[)') && tstzrange(b.pickup_date, b.return_date, '[)')",
                Integer.class);
        assertThat(crossings).isZero();
        assertThat(outcomes.stream().filter(Outcome::created).count()).isBetween(1L, 3L);
    }

    @Test
    void nonOverlappingRanges_areAllAcceptedEvenIfSimultaneous() throws Exception {
        Instant base = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < PEOPLE; i++) {
            Instant pickup = base.plus(i * 5L, ChronoUnit.DAYS); // 2 días cada una, separadas: no se cruzan
            tasks.add(attempt(emails.get(i), pickup, pickup.plus(2, ChronoUnit.DAYS)));
        }

        List<Outcome> outcomes = fireSimultaneously(tasks);

        assertThat(outcomes).allSatisfy(o -> assertThat(o.created()).as(o.email() + " " + o.error()).isTrue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental.reservations", Integer.class)).isEqualTo(PEOPLE);
    }

    @Test
    void databaseExclusionConstraintRejectsAnOverlapEvenWithoutTheApplicationLock() {
        // Segunda capa: la misma regla en PostgreSQL. Se instala como en las migraciones (hu-bd-27) y se prueba con SQL directo.
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS btree_gist");
        jdbc.execute("ALTER TABLE rental.reservations ADD CONSTRAINT reservations_vehicle_period_no_overlap "
                + "EXCLUDE USING GIST (vehicle_id WITH =, tstzrange(pickup_date, return_date, '[)') WITH &&) WHERE (blocks_availability)");

        Instant pickup = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        reservationService.createReservation(request(pickup, pickup.plus(3, ChronoUnit.DAYS)), emails.get(0));

        // Un INSERT directo que se cruza (saltándose por completo el servicio y su bloqueo) debe fallar en la base
        Throwable error = org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "INSERT INTO rental.reservations (id, code, customer_id, vehicle_id, status_id, insurance_coverage_id, mileage_plan_id, "
                        + "pickup_date, return_date, daily_rate, total_estimated, blocks_availability, created_at, updated_at) "
                        + "SELECT gen_random_uuid(), 'RAW-1', u.id, ?::uuid, s.id, ?::uuid, ?::uuid, ?, ?, 220000, 1, true, now(), now() "
                        + "FROM iam.users u, rental.reservation_statuses s WHERE u.email = ? AND s.code = 'PENDING_PAYMENT'",
                vehicleId.toString(), insuranceId.toString(), mileagePlanId.toString(),
                java.sql.Timestamp.from(pickup.plus(1, ChronoUnit.DAYS)), java.sql.Timestamp.from(pickup.plus(2, ChronoUnit.DAYS)), emails.get(1)));

        assertThat(error).isNotNull();
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        assertThat(root).isInstanceOf(SQLException.class);
        // 23P01 = exclusion_violation
        assertThat(((SQLException) root).getSQLState()).isEqualTo("23P01");
    }
}
