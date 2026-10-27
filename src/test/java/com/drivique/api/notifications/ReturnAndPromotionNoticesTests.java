package com.drivique.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.PromotionNotificationService;
import com.drivique.api.service.ReturnReminderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev")
class ReturnAndPromotionNoticesTests extends DatabaseHealthTestSupport {

    @Autowired private ReturnReminderService reminders;
    @Autowired private PromotionNotificationService promotionNotices;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private BranchUserRepository branchUsers;
    @Autowired private DepartmentRepository departments;
    @Autowired private CityRepository cities;
    @Autowired private BranchRepository branchRepository;
    @Autowired private VehicleBrandRepository brands;
    @Autowired private VehicleCategoryRepository categories;
    @Autowired private TransmissionTypeRepository transmissions;
    @Autowired private FuelTypeRepository fuels;
    @Autowired private VehicleStatusRepository vehicleStatuses;
    @Autowired private VehicleRepository vehicles;
    @Autowired private InsuranceCoverageRepository insurances;
    @Autowired private MileagePlanRepository mileagePlans;
    @Autowired private ReservationStatusRepository reservationStatuses;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PromotionRepository promotions;

    private User customer;
    private Vehicle vehicle;
    private ReservationStatus inProgress;
    private InsuranceCoverage insurance;
    private MileagePlan plan;
    private Branch branch;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();
        jdbc.execute("DELETE FROM catalog.promotions");

        Role customerRole = roles.save(new Role("CUSTOMER", "Customer", "Customer", true));
        Role branchAdmin = roles.save(new Role("BRANCH_ADMIN", "Branch Administrator", "Branch", true));
        customer = new User("Carlos", "Gomez", "customer@drivique.com", "x");
        customer.setRoles(Set.of(customerRole));
        customer = users.saveAndFlush(customer);
        User manager = new User("Laura", "Encargada", "laura@drivique.com", "x");
        manager.setRoles(Set.of(branchAdmin));
        manager = users.saveAndFlush(manager);

        City city = cities.saveAndFlush(new City(departments.saveAndFlush(new Department("Antioquia")), "Medellín", true, true));
        branch = branchRepository.saveAndFlush(new Branch("Sede Poblado", "Cra 43A", city, "3001", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        branchUsers.saveAndFlush(new BranchUser(manager, branch));

        VehicleBrand brand = brands.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categories.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("200000"), new BigDecimal("1500000")));
        TransmissionType transmission = transmissions.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuels.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus available = vehicleStatuses.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));
        vehicle = vehicles.saveAndFlush(new Vehicle("AAA111", "1HGCR2F83HA000001", brand, category, transmission, fuel, available, branch,
                "Corolla Cross", (short) 2024, "Blanco", (short) 5, (short) 5, 500, 15000, new BigDecimal("220000.00"), null, true));
        insurance = insurances.saveAndFlush(new InsuranceCoverage("Básica", new BigDecimal("35000.00"), "Protección"));
        plan = mileagePlans.saveAndFlush(new MileagePlan("200km", 200, new BigDecimal("15000.00"), new BigDecimal("500.00")));
        inProgress = reservationStatuses.saveAndFlush(new ReservationStatus("IN_PROGRESS", "In progress", true));
    }

    private void reservation(String code, Instant pickup, Instant ret) {
        reservationRepository.saveAndFlush(new Reservation(code, customer, vehicle, inProgress, insurance, plan, branch, pickup, ret,
                new BigDecimal("220000.00"), new BigDecimal("500000.00"), null, null, true));
    }

    private int count(String where) {
        return jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE " + where, Integer.class);
    }

    @Test
    void customerIsRemindedTheDayBeforeTheReturnAndOnlyOnce() {
        Instant tomorrowNoon = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1).atTime(12, 0).atZone(ZoneId.of("America/Bogota")).toInstant();
        reservation("RES-1D", tomorrowNoon.minus(3, ChronoUnit.DAYS), tomorrowNoon);

        assertThat(reminders.sendReminders()).isGreaterThan(0);
        assertThat(count("subject = 'Tu devolución es mañana' AND channel = 'IN_APP'")).isEqualTo(1);
        assertThat(count("subject = 'Tu devolución es mañana' AND channel = 'EMAIL'")).isEqualTo(1);

        reminders.sendReminders();
        assertThat(count("subject = 'Tu devolución es mañana' AND channel = 'IN_APP'")).isEqualTo(1);
    }

    @Test
    void overdueReservationsNotifyTheCustomerAndTheBranchStaff() {
        reservation("RES-OVERDUE", Instant.now().minus(5, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));

        reminders.sendReminders();
        assertThat(count("subject = 'Tu alquiler ya venció'")).isEqualTo(2);
        assertThat(count("subject = 'Reserva vencida sin devolver'")).isEqualTo(1);
    }

    @Test
    void customerAndStaffAreNotifiedTheMomentTheReservationExpiresAndOnlyOnce() {
        reservation("RES-EXPIRES-NOW", Instant.now().minus(5, ChronoUnit.DAYS), Instant.now().minusSeconds(30));

        assertThat(reminders.sendOverdueNotices()).isEqualTo(3);
        assertThat(count("subject = 'Tu alquiler ya venció' AND channel = 'IN_APP'")).isEqualTo(1);
        assertThat(count("subject = 'Tu alquiler ya venció' AND channel = 'EMAIL'")).isEqualTo(1);
        assertThat(count("subject = 'Reserva vencida sin devolver'")).isEqualTo(1);

        // La tarea corre cada minuto: el segundo pase no crea avisos repetidos
        reminders.sendOverdueNotices();
        assertThat(count("subject = 'Tu alquiler ya venció'")).isEqualTo(2);
        assertThat(count("subject = 'Reserva vencida sin devolver'")).isEqualTo(1);
    }

    @Test
    void theScheduledEntryPointsRunInsideATransactionSoLazyCustomersCanBeRead() {
        reservation("RES-SCHEDULED", Instant.now().minus(5, ChronoUnit.DAYS), Instant.now().minusSeconds(30));

        // Es el método que invoca el programador: antes fallaba con LazyInitializationException en producción
        reminders.scheduledOverdue();
        assertThat(count("subject = 'Tu alquiler ya venció'")).isEqualTo(2);
        assertThat(count("subject = 'Reserva vencida sin devolver'")).isEqualTo(1);
    }

    @Test
    void reservationNotYetExpiredIsNotNotifiedByTheExactTimeJob() {
        reservation("RES-STILL-OPEN", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(reminders.sendOverdueNotices()).isZero();
        assertThat(count("subject = 'Tu alquiler ya venció'")).isZero();
    }

    @Test
    void theDailyJobDoesNotRepeatTheNoticeOfAReservationThatExpiredToday() {
        Instant startOfToday = LocalDate.now(ZoneId.of("America/Bogota")).atStartOfDay(ZoneId.of("America/Bogota")).toInstant();
        Instant expiredEarlierToday = startOfToday.plusSeconds(1);
        org.junit.jupiter.api.Assumptions.assumeTrue(expiredEarlierToday.isBefore(Instant.now()));
        reservation("RES-EXPIRED-TODAY", startOfToday.minus(3, ChronoUnit.DAYS), expiredEarlierToday);

        reminders.sendOverdueNotices();
        reminders.sendReminders();

        assertThat(count("subject = 'Tu alquiler ya venció'")).isEqualTo(2);
        assertThat(count("subject = 'Reserva vencida sin devolver'")).isEqualTo(1);
    }

    @Test
    void reservationsFarFromTheirReturnAreNotNotified() {
        reservation("RES-LATER", Instant.now(), Instant.now().plus(10, ChronoUnit.DAYS));
        assertThat(reminders.sendReminders()).isZero();
    }

    @Test
    void activeValidPromotionsAreAnnouncedToCustomersOnlyOnce() {
        promotions.saveAndFlush(new Promotion("VERANO15", "COUPON", "PERCENTAGE", new BigDecimal("15"),
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), 1, null, 0));
        promotions.saveAndFlush(new Promotion("VENCIDA", "COUPON", "PERCENTAGE", new BigDecimal("10"),
                Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS), 1, null, 0));

        assertThat(promotionNotices.notifyPending()).isEqualTo(2);
        assertThat(count("type = 'PROMOTION' AND subject LIKE '%VERANO15%'")).isEqualTo(2);
        assertThat(count("subject LIKE '%VENCIDA%'")).isZero();

        assertThat(promotionNotices.notifyPending()).isZero();
    }
}
