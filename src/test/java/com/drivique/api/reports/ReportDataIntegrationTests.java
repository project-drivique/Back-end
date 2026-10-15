package com.drivique.api.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.ReportDataService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ReportDataIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private ReportDataService reportData;
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
    @Autowired private ReservationRepository reservations;

    private Branch poblado;
    private Branch laureles;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reportData, "maxExportRows", 5000);
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();
        jdbc.execute("DELETE FROM audit.generated_reports");

        Role superAdmin = roles.save(new Role("SUPER_ADMIN", "Super Administrator", "Admin role", true));
        Role branchAdmin = roles.save(new Role("BRANCH_ADMIN", "Branch Administrator", "Branch role", true));
        User admin = new User("Admin", "General", "admin@drivique.com", "x");
        admin.setRoles(Set.of(superAdmin));
        users.save(admin);
        User customer = users.saveAndFlush(new User("Carlos", "Gomez", "customer@drivique.com", "x"));

        City city = cities.saveAndFlush(new City(departments.saveAndFlush(new Department("Antioquia")), "Medellín", true, true));
        poblado = branchRepository.saveAndFlush(new Branch("Sede Poblado", "Cra 43A", city, "3001", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        laureles = branchRepository.saveAndFlush(new Branch("Sede Laureles", "Cra 70", city, "3002", LocalTime.of(8, 0), LocalTime.of(18, 0), true));

        User manager = new User("Laura", "Encargada", "laura@drivique.com", "x");
        manager.setRoles(Set.of(branchAdmin));
        manager = users.saveAndFlush(manager);
        branchUsers.saveAndFlush(new BranchUser(manager, poblado));

        VehicleBrand brand = brands.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categories.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("200000"), new BigDecimal("1500000")));
        TransmissionType transmission = transmissions.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuels.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus available = vehicleStatuses.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));
        Vehicle v1 = vehicle("AAA111", "1HGCR2F83HA000001", brand, category, transmission, fuel, available, poblado);
        Vehicle v2 = vehicle("BBB222", "1HGCR2F83HA000002", brand, category, transmission, fuel, available, laureles);

        InsuranceCoverage insurance = insurances.saveAndFlush(new InsuranceCoverage("Básica", new BigDecimal("35000.00"), "Protección"));
        MileagePlan plan = mileagePlans.saveAndFlush(new MileagePlan("200km", 200, new BigDecimal("15000.00"), new BigDecimal("500.00")));
        ReservationStatus confirmed = reservationStatuses.saveAndFlush(new ReservationStatus("CONFIRMED", "Confirmed", true));
        ReservationStatus rejected = reservationStatuses.saveAndFlush(new ReservationStatus("REJECTED", "Rejected", false));

        Instant now = Instant.now();
        reservations.saveAndFlush(new Reservation("RES-PO-1", customer, v1, confirmed, insurance, plan, poblado,
                now.plus(1, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS), new BigDecimal("200000.00"), new BigDecimal("500000.00"), null, null, true));
        reservations.saveAndFlush(new Reservation("RES-PO-2", customer, v1, rejected, insurance, plan, poblado,
                now.plus(10, ChronoUnit.DAYS), now.plus(12, ChronoUnit.DAYS), new BigDecimal("200000.00"), new BigDecimal("300000.00"), null, null, false));
        reservations.saveAndFlush(new Reservation("RES-LA-1", customer, v2, confirmed, insurance, plan, laureles,
                now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), new BigDecimal("200000.00"), new BigDecimal("250000.00"), null, null, true));
    }

    private Vehicle vehicle(String plate, String vin, VehicleBrand brand, VehicleCategory category, TransmissionType transmission,
                            FuelType fuel, VehicleStatus status, Branch branch) {
        return vehicles.saveAndFlush(new Vehicle(plate, vin, brand, category, transmission, fuel, status, branch, "Corolla Cross",
                (short) 2024, "Blanco", (short) 5, (short) 5, 500, 15000, new BigDecimal("220000.00"), null, true));
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void reservationReportMatchesTheControlQuery() throws Exception {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM rental.reservations", Integer.class);
        Long value = jdbc.queryForObject("SELECT sum(total_estimated)::bigint FROM rental.reservations", Long.class);

        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(total))
                .andExpect(jsonPath("$.summary[?(@.key=='total')].value").value(total))
                .andExpect(jsonPath("$.summary[?(@.key=='value')].value").value(value.intValue()))
                .andExpect(jsonPath("$.summary[?(@.key=='confirmed')].value").value(2))
                .andExpect(jsonPath("$.summary[?(@.key=='cancelled')].value").value(1))
                .andExpect(jsonPath("$.columns[*].key", hasItem("code")));
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void filtersAndPaginationAreAppliedInTheDatabase() throws Exception {
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.rows.length()").value(1));
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("status", "REJECTED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.rows[0].code").value("RES-PO-2"));
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("branchId", laureles.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.rows[0].code").value("RES-LA-1"));
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void everyReportTypeIsCalculatedByTheServer() throws Exception {
        for (String type : ReportDataService.TYPES) {
            mvc.perform(get("/v1/admin/reports/data/" + type))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value(type))
                    .andExpect(jsonPath("$.columns.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                    .andExpect(jsonPath("$.summary.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        }
        mvc.perform(get("/v1/admin/reports/data/VEHICLES"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.summary[?(@.key=='available')].value").value(2));
        mvc.perform(get("/v1/admin/reports/data/BRANCHES"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.summary[?(@.key=='vehicles')].value").value(2));
        mvc.perform(get("/v1/admin/reports/data/FLEET_OCCUPANCY"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/reports/data/NOPE")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("from", "2026-02-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("from", "2020-01-01").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "laura@drivique.com", roles = "BRANCH_ADMIN")
    void aBranchManagerOnlySeesTheirOwnBranch() throws Exception {
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("branchId", laureles.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/reports/data/BRANCHES"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void customersCannotUseReports() throws Exception {
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS")).andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"RESERVATIONS\",\"format\":\"CSV\"}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void exportsAreGeneratedOnTheServerAndRegisteredInTheHistoryAndAudit() throws Exception {
        MvcResult csv = mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESERVATIONS\",\"format\":\"CSV\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().exists("X-Report-Code"))
                .andReturn();
        String body = csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("RES-PO-1").contains("RES-LA-1").contains("Reservas");

        byte[] pdf = mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"REVENUE_SUMMARY\",\"format\":\"PDF\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        byte[] xlsx = mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VEHICLES\",\"format\":\"EXCEL\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.generated_reports", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_logs WHERE domain_name = 'REPORTS' AND operation = 'INSERT'",
                Integer.class)).isEqualTo(3);

        String id = jdbc.queryForObject("SELECT id::text FROM audit.generated_reports ORDER BY generated_at LIMIT 1", String.class);
        byte[] again = mvc.perform(get("/v1/admin/reports/history/" + id + "/download").param("format", "CSV"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(again, java.nio.charset.StandardCharsets.UTF_8)).contains("RES-PO-1");
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void exportsAboveTheLimitAreRefused() throws Exception {
        ReflectionTestUtils.setField(reportData, "maxExportRows", 2);
        mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESERVATIONS\",\"format\":\"CSV\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESERVATIONS\",\"format\":\"PPT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void csvCellsThatLookLikeFormulasAreNeutralised() throws Exception {
        jdbc.execute("UPDATE iam.users SET first_name = '=HYPERLINK(1)' WHERE email = 'customer@drivique.com'");
        String body = mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESERVATIONS\",\"format\":\"CSV\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("\"'=HYPERLINK(1) Gomez\"").doesNotContain("\"=HYPERLINK");
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void dashboardNumbersComeFromTheDatabase() throws Exception {
        mvc.perform(get("/v1/admin/reports/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleet.total").value(2))
                .andExpect(jsonPath("$.fleet.available").value(org.hamcrest.Matchers.lessThanOrEqualTo(2)))
                .andExpect(jsonPath("$.payments.total").value(2))
                .andExpect(jsonPath("$.branches.length()").value(2))
                .andExpect(jsonPath("$.branches[?(@.branch=='Sede Poblado')].reservations").value(2))
                .andExpect(jsonPath("$.months.length()").value(java.time.LocalDate.now().getMonthValue()));
    }

    @Test
    @WithMockUser(username = "laura@drivique.com", roles = "BRANCH_ADMIN")
    void dashboardForAManagerIsLimitedToTheirBranch() throws Exception {
        mvc.perform(get("/v1/admin/reports/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleet.total").value(1))
                .andExpect(jsonPath("$.branches.length()").value(1))
                .andExpect(jsonPath("$.branches[0].branch").value("Sede Poblado"));
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void searchFiltersRowsByText() throws Exception {
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("search", "RES-LA"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.rows[0].code").value("RES-LA-1"));
        mvc.perform(get("/v1/admin/reports/data/VEHICLES").param("search", "bbb"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/v1/admin/reports/data/USERS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/v1/admin/reports/data/EXECUTIVE_SUMMARY"))
                .andExpect(jsonPath("$.rows.length()").value(6))
                .andExpect(jsonPath("$.summary[?(@.key=='reservations')].value").value(3));
    }

    @Test
    @WithMockUser(username = "laura@drivique.com", roles = "BRANCH_ADMIN")
    void managersCannotListUsers() throws Exception {
        mvc.perform(get("/v1/admin/reports/data/USERS")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void severalStatusesAndWordExportAreSupported() throws Exception {
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("status", "CONFIRMED,REJECTED"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/v1/admin/reports/data/RESERVATIONS").param("status", "REJECTED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        String word = mvc.perform(post("/v1/admin/reports/export").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESERVATIONS\",\"format\":\"WORD\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", containsString("msword")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(word).contains("<table>").contains("RES-PO-1");
    }
}
