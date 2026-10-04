package com.drivique.api.reservations;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.dto.CreateExtensionRequestDTO;
import com.drivique.api.dto.ReviewExtensionRequestDTO;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class RentalExtensionIntegrationTests extends DatabaseHealthTestSupport {

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
    @Autowired private RentalExtensionRequestRepository extensionRepository;

    private User customer;
    @SuppressWarnings("unused")
    private User admin;
    private Vehicle vehicle;
    private Branch branch;
    private InsuranceCoverage insurance;
    private MileagePlan mileagePlan;
    private ReservationStatus confirmedStatus;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        resetRentalTables();
        resetCatalogTables();
        resetFleetTables();
        resetLocationTables();
        resetIamTables();

        customer = userRepository.saveAndFlush(new User("Carlos", "Gomez", "customer@drivique.com", "$2a$10$hash"));
        admin = userRepository.saveAndFlush(new User("Admin", "User", "admin@drivique.com", "$2a$10$hash"));

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
                "EXT123",
                "1HGCR2F83HA000999",
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
                new BigDecimal("200000.00"),
                null,
                true
        ));

        insurance = insuranceRepository.saveAndFlush(new InsuranceCoverage("Básica", new BigDecimal("30000.00"), "Protección"));
        mileagePlan = mileagePlanRepository.saveAndFlush(new MileagePlan("200km", 200, new BigDecimal("10000.00"), new BigDecimal("500.00")));
        confirmedStatus = reservationStatusRepository.saveAndFlush(new ReservationStatus("CONFIRMED", "Confirmed", true));

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        reservation = reservationRepository.saveAndFlush(new Reservation(
                "RES-2026-EXT1",
                customer,
                vehicle,
                confirmedStatus,
                insurance,
                mileagePlan,
                branch,
                now.plus(1, ChronoUnit.DAYS),
                now.plus(4, ChronoUnit.DAYS),
                new BigDecimal("200000.00"),
                new BigDecimal("720000.00"),
                null,
                null,
                true
        ));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void requestExtension_Success() throws Exception {
        Instant requestedDate = reservation.getReturnDate().plus(2, ChronoUnit.DAYS);
        CreateExtensionRequestDTO requestDTO = new CreateExtensionRequestDTO(requestedDate);

        mvc.perform(post("/v1/reservations/{id}/extensions", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId", is(reservation.getId().toString())))
                .andExpect(jsonPath("$.reservationCode", is("RES-2026-EXT1")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.additionalDays", is(2)))
                .andExpect(jsonPath("$.additionalAmount", is(480000.00)));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void requestExtension_Conflict_WhenOverlappingReservationExists() throws Exception {
        Instant requestedDate = reservation.getReturnDate().plus(2, ChronoUnit.DAYS);

        // Seed conflicting reservation
        User otherCustomer = userRepository.saveAndFlush(new User("Ana", "Lopez", "ana@drivique.com", "$2a$10$hash"));
        Reservation conflictingReservation = new Reservation(
                "RES-2026-CONF",
                otherCustomer,
                vehicle,
                confirmedStatus,
                insurance,
                mileagePlan,
                branch,
                reservation.getReturnDate().plus(1, ChronoUnit.HOURS),
                requestedDate.plus(1, ChronoUnit.DAYS),
                new BigDecimal("200000.00"),
                new BigDecimal("500000.00"),
                null,
                null,
                true
        );
        reservationRepository.saveAndFlush(conflictingReservation);

        CreateExtensionRequestDTO requestDTO = new CreateExtensionRequestDTO(requestedDate);

        mvc.perform(post("/v1/reservations/{id}/extensions", reservation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "ADMIN")
    void reviewExtension_Approved_UpdatesReservation() throws Exception {
        Instant requestedDate = reservation.getReturnDate().plus(2, ChronoUnit.DAYS);
        RentalExtensionRequest ext = extensionRepository.saveAndFlush(new RentalExtensionRequest(
                reservation,
                requestedDate,
                new BigDecimal("480000.00")
        ));

        ReviewExtensionRequestDTO reviewDTO = new ReviewExtensionRequestDTO("APPROVED", "Extensión autorizada");

        mvc.perform(patch("/v1/admin/extensions/{id}", ext.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.reviewedByUserName", is("Admin User")));

        Reservation updatedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updatedReservation.getReturnDate()).isEqualTo(requestedDate);
        assertThat(updatedReservation.getTotalEstimated()).isEqualByComparingTo("1200000.00");
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "ADMIN")
    void reviewExtension_Rejected_DoesNotUpdateReservation() throws Exception {
        Instant originalReturnDate = reservation.getReturnDate();
        Instant requestedDate = originalReturnDate.plus(2, ChronoUnit.DAYS);
        RentalExtensionRequest ext = extensionRepository.saveAndFlush(new RentalExtensionRequest(
                reservation,
                requestedDate,
                new BigDecimal("480000.00")
        ));

        ReviewExtensionRequestDTO reviewDTO = new ReviewExtensionRequestDTO("REJECTED", "Rechazada por mantenimiento");

        mvc.perform(patch("/v1/admin/extensions/{id}", ext.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));

        Reservation updatedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(updatedReservation.getReturnDate()).isEqualTo(originalReturnDate);
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void reviewExtension_ForbiddenForCustomer() throws Exception {
        UUID randomId = UUID.randomUUID();
        ReviewExtensionRequestDTO reviewDTO = new ReviewExtensionRequestDTO("APPROVED", "Hack");

        mvc.perform(patch("/v1/admin/extensions/{id}", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewExtension_UnauthorizedWithoutAuth() throws Exception {
        UUID randomId = UUID.randomUUID();
        ReviewExtensionRequestDTO reviewDTO = new ReviewExtensionRequestDTO("APPROVED", "Hack");

        mvc.perform(patch("/v1/admin/extensions/{id}", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewDTO)))
                .andExpect(status().isUnauthorized());
    }
}
