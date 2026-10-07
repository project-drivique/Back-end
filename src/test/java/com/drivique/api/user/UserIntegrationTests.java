package com.drivique.api.user;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class UserIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private VehicleBrandRepository brandRepository;

    @Autowired
    private VehicleCategoryRepository categoryRepository;

    @Autowired
    private TransmissionTypeRepository transmissionRepository;

    @Autowired
    private FuelTypeRepository fuelRepository;

    @Autowired
    private VehicleStatusRepository vehicleStatusRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsuranceCoverageRepository insuranceRepository;

    @Autowired
    private MileagePlanRepository mileagePlanRepository;

    @Autowired
    private ReservationStatusRepository reservationStatusRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();

        Role customerRole = roleRepository.save(new Role(
                "CUSTOMER",
                "Customer",
                "Customer role",
                true
        ));

        testUser = new User("Ana", "Martinez", "ana.martinez@drivique.com", passwordEncoder.encode("SecurePass123!"));
        testUser.setPhone("+573009876543");
        testUser.setDocumentNumber("1122334455");
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);

        jwtToken = jwtService.generateAccessToken(
                testUser.getId(),
                testUser.getEmail(),
                testUser.getFullName(),
                List.of("CUSTOMER")
        );
    }

    @Test
    void getMyProfileWithoutAuthReturns401() throws Exception {
        mvc.perform(get("/api/v1/users/me").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfileWithAuthReturnsUserData() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana.martinez@drivique.com"))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Martinez"))
                .andExpect(jsonPath("$.phone").value("+573009876543"));
    }

    @Test
    void updateMyProfileUpdatesNonCriticalFields() throws Exception {
        String updateBody = """
                {
                    "firstName": "Ana Maria",
                    "lastName": "Martinez Rodriguez",
                    "phone": "+573123456789",
                    "birthDate": "1992-04-10",
                    "nationalityId": null
                }
                """;

        mvc.perform(put("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana Maria"))
                .andExpect(jsonPath("$.lastName").value("Martinez Rodriguez"))
                .andExpect(jsonPath("$.phone").value("+573123456789"))
                .andExpect(jsonPath("$.birthDate").value("1992-04-10"));

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getFirstName()).isEqualTo("Ana Maria");
        assertThat(updatedUser.getEmail()).isEqualTo("ana.martinez@drivique.com"); // Remains untouched
    }

    @Test
    void deleteMyAccountRemovesUserWhenPasswordMatches() throws Exception {
        mvc.perform(delete("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"SecurePass123!\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(testUser.getId())).isEmpty();
        assertThat(userRepository.findByEmailIgnoreCase("ana.martinez@drivique.com")).isEmpty();
    }

    @Test
    void deleteMyAccountRejectsIncorrectPassword() throws Exception {
        mvc.perform(delete("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"WrongPass123!\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findById(testUser.getId())).isPresent();
    }

    @Test
    void deleteMyAccountWithOperationsAnonymizesProfileAndPreservesOperationalRecords() throws Exception {
        Department department = departmentRepository.saveAndFlush(new Department("Antioquia-Del"));
        City city = cityRepository.saveAndFlush(new City(department, "Medellín-Del", true, true));
        Branch branch = branchRepository.saveAndFlush(new Branch(
                "Sede Poblado Del",
                "Cra 43A # 1-50 Del",
                city,
                "3001234567",
                LocalTime.of(8, 0),
                LocalTime.of(18, 0),
                true
        ));

        VehicleBrand brand = brandRepository.saveAndFlush(new VehicleBrand("Toyota-Del"));
        VehicleCategory category = categoryRepository.saveAndFlush(new VehicleCategory("SUV-Del", new BigDecimal("200000"), new BigDecimal("1500000")));
        TransmissionType transmission = transmissionRepository.saveAndFlush(new TransmissionType("AUTO-DEL", "Automatic Del"));
        FuelType fuel = fuelRepository.saveAndFlush(new FuelType("GAS-DEL", "Gasoline Del"));
        VehicleStatus status = vehicleStatusRepository.saveAndFlush(new VehicleStatus("AVAIL-DEL", "Available Del", true));

        Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle(
                "DEL1234", "1HGCR2F83HA000999", brand, category, transmission, fuel, status, branch,
                "Corolla Cross Del", (short) 2024, "Blanco", (short) 5, (short) 5, 500, 15000,
                new BigDecimal("220000.00"), null, true
        ));

        InsuranceCoverage insurance = insuranceRepository.saveAndFlush(new InsuranceCoverage("Básica Del", new BigDecimal("35000.00"), "Protección"));
        MileagePlan mileagePlan = mileagePlanRepository.saveAndFlush(new MileagePlan("200km Del", 200, new BigDecimal("15000.00"), new BigDecimal("500.00")));
        ReservationStatus confirmedStatus = reservationStatusRepository.saveAndFlush(new ReservationStatus("CONF-DEL", "Confirmed Del", true));

        Instant now = Instant.now();
        Reservation reservation = reservationRepository.saveAndFlush(new Reservation(
                "RES-2026-DEL1",
                testUser,
                vehicle,
                confirmedStatus,
                insurance,
                mileagePlan,
                branch,
                now.plus(1, ChronoUnit.DAYS),
                now.plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000.00"),
                new BigDecimal("810000.00"),
                null,
                null,
                true
        ));

        mvc.perform(delete("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"SecurePass123!\"}"))
                .andExpect(status().isNoContent());

        // El usuario permanece en BD para sostener la clave foránea de la reserva/contrato
        User retainedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(retainedUser.getAccountStatus()).isEqualTo("DELETED");
        assertThat(retainedUser.getDeletedAt()).isNotNull();
        assertThat(retainedUser.getFirstName()).isEqualTo("Usuario");
        assertThat(retainedUser.getLastName()).isEqualTo("Eliminado");
        assertThat(retainedUser.getPhone()).isNull();
        assertThat(retainedUser.getPasswordHash()).startsWith("DELETED_");

        // La reserva permanece intacta
        assertThat(reservationRepository.findById(reservation.getId())).isPresent();

        // El usuario eliminado ya no puede consultar su perfil (404/401)
        mvc.perform(get("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyPreferencesReturnsDefaultsInitially() throws Exception {
        mvc.perform(get("/api/v1/users/me/preferences")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themePreference").value("SYSTEM"))
                .andExpect(jsonPath("$.emailNotifications").value(true))
                .andExpect(jsonPath("$.smsNotifications").value(true));
    }

    @Test
    void updateMyPreferencesUpdatesConfig() throws Exception {
        String updateBody = """
                {
                    "themePreference": "DARK",
                    "emailNotifications": false,
                    "smsNotifications": true
                }
                """;

        mvc.perform(put("/api/v1/users/me/preferences")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themePreference").value("DARK"))
                .andExpect(jsonPath("$.emailNotifications").value(false))
                .andExpect(jsonPath("$.smsNotifications").value(true));
    }
}
