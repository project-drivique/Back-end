package com.drivique.api.contracts;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.dto.GenerateContractRequestDTO;
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
class ContractIntegrationTests extends DatabaseHealthTestSupport {

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
    @Autowired private ContractStatusRepository contractStatusRepository;
    @Autowired private ContractClauseRepository contractClauseRepository;
    @Autowired private RentalContractRepository rentalContractRepository;

    private User customer;
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
        VehicleCategory category = categoryRepository.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("200000"), new BigDecimal("1500000")));
        TransmissionType transmission = transmissionRepository.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuelRepository.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus status = vehicleStatusRepository.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));

        vehicle = vehicleRepository.saveAndFlush(new Vehicle(
                "CTR123",
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
                15000,
                new BigDecimal("220000.00"),
                null,
                true
        ));

        insurance = insuranceRepository.saveAndFlush(new InsuranceCoverage("Básica", new BigDecimal("35000.00"), "Protección"));
        mileagePlan = mileagePlanRepository.saveAndFlush(new MileagePlan("200km", 200, new BigDecimal("15000.00"), new BigDecimal("500.00")));
        confirmedStatus = reservationStatusRepository.saveAndFlush(new ReservationStatus("CONFIRMED", "Confirmed", true));

        Instant now = Instant.now();
        reservation = reservationRepository.saveAndFlush(new Reservation(
                "RES-2026-CTR1",
                customer,
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

        contractStatusRepository.saveAndFlush(new ContractStatus("DRAFT", "Draft", true, false));
        contractStatusRepository.saveAndFlush(new ContractStatus("PENDING_SIGNATURE", "Pending signature", true, false));
        contractClauseRepository.saveAndFlush(new ContractClause("v1.0", (short) 1, "Objeto del Contrato", "Texto objeto", true));
        contractClauseRepository.saveAndFlush(new ContractClause("v1.0", (short) 2, "Uso y Destinación", "Texto uso", true));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void generateContract_Success() throws Exception {
        GenerateContractRequestDTO requestDTO = new GenerateContractRequestDTO(reservation.getId());

        mvc.perform(post("/v1/contracts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contractNumber", startsWith("CTR-")))
                .andExpect(jsonPath("$.reservationCode", is("RES-2026-CTR1")))
                .andExpect(jsonPath("$.statusCode", is("PENDING_SIGNATURE")))
                .andExpect(jsonPath("$.baseAmount", is(810000.00)))
                .andExpect(jsonPath("$.securityDeposit", is(1500000.00)))
                .andExpect(jsonPath("$.clauses", hasSize(2)));

        assertThat(rentalContractRepository.existsByReservationId(reservation.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void generateContract_Conflict_WhenAlreadyGenerated() throws Exception {
        GenerateContractRequestDTO requestDTO = new GenerateContractRequestDTO(reservation.getId());

        // First generation
        mvc.perform(post("/v1/contracts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated());

        // Duplicate generation
        mvc.perform(post("/v1/contracts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isConflict());
    }

    @Test
    void getClauses_Success_PermitAll() throws Exception {
        mvc.perform(get("/v1/contracts/clauses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Objeto del Contrato")));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void getContractById_Success() throws Exception {
        GenerateContractRequestDTO requestDTO = new GenerateContractRequestDTO(reservation.getId());

        String responseBody = mvc.perform(post("/v1/contracts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID contractId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

        mvc.perform(get("/v1/contracts/{id}", contractId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(contractId.toString())))
                .andExpect(jsonPath("$.customerFullName", is("Carlos Gomez")));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com", roles = "CUSTOMER")
    void getContractByReservationId_Success() throws Exception {
        GenerateContractRequestDTO requestDTO = new GenerateContractRequestDTO(reservation.getId());

        mvc.perform(post("/v1/contracts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated());

        mvc.perform(get("/v1/contracts/reservation/{reservationId}", reservation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId", is(reservation.getId().toString())));
    }
}
