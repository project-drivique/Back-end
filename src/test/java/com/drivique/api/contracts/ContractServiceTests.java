package com.drivique.api.contracts;

import com.drivique.api.dto.ContractResponseDTO;
import com.drivique.api.dto.GenerateContractRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.ClauseService;
import com.drivique.api.service.ContractService;
import com.drivique.api.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractServiceTests {

    private final RentalContractRepository rentalContractRepository = mock(RentalContractRepository.class);
    private final ContractStatusRepository contractStatusRepository = mock(ContractStatusRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final ClauseService clauseService = mock(ClauseService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private ContractService service;

    private final String customerEmail = "customer@drivique.com";
    private final UUID reservationId = UUID.randomUUID();
    private final UUID contractId = UUID.randomUUID();

    private User customer;
    private Vehicle vehicle;
    private VehicleCategory category;
    private Branch branch;
    private Reservation reservation;
    private ReservationStatus confirmedStatus;
    private ContractStatus draftStatus;
    private List<ContractClause> clauses;

    @BeforeEach
    void setUp() {
        service = new ContractService(
                rentalContractRepository,
                contractStatusRepository,
                reservationRepository,
                userRepository,
                branchRepository,
                clauseService,
                fileStorageService
        );

        customer = mock(User.class);
        when(customer.getId()).thenReturn(UUID.randomUUID());
        when(customer.getEmail()).thenReturn(customerEmail);
        when(customer.getFirstName()).thenReturn("Carlos");
        when(customer.getLastName()).thenReturn("Gomez");
        when(customer.getRoles()).thenReturn(Collections.emptySet());

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(customerEmail)).thenReturn(Optional.of(customer));

        branch = mock(Branch.class);
        when(branch.getId()).thenReturn(UUID.randomUUID());
        when(branch.getName()).thenReturn("Sede Poblado");

        City city = mock(City.class);
        when(city.getId()).thenReturn(UUID.randomUUID());
        when(city.getName()).thenReturn("Medellín");
        when(branch.getCity()).thenReturn(city);

        category = mock(VehicleCategory.class);
        when(category.getSecurityDeposit()).thenReturn(new BigDecimal("1500000.00"));

        vehicle = mock(Vehicle.class);
        when(vehicle.getId()).thenReturn(UUID.randomUUID());
        when(vehicle.getModel()).thenReturn("Corolla Cross");
        when(vehicle.getPlate()).thenReturn("DRV777");
        when(vehicle.getCategory()).thenReturn(category);
        when(vehicle.getCurrentBranch()).thenReturn(branch);

        confirmedStatus = new ReservationStatus("CONFIRMED", "Confirmed", true);
        draftStatus = new ContractStatus("DRAFT", "Draft", true, false);

        Instant now = Instant.now();
        reservation = new Reservation(
                "RES-2026-0001",
                customer,
                vehicle,
                confirmedStatus,
                mock(InsuranceCoverage.class),
                mock(MileagePlan.class),
                branch,
                now.plus(1, ChronoUnit.DAYS),
                now.plus(4, ChronoUnit.DAYS),
                new BigDecimal("220000.00"),
                new BigDecimal("800000.00"),
                null,
                null,
                true
        );

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(contractStatusRepository.findByCodeIgnoreCase("DRAFT")).thenReturn(Optional.of(draftStatus));
        when(contractStatusRepository.findByCodeIgnoreCase("PENDING_SIGNATURE"))
                .thenReturn(Optional.of(new ContractStatus("PENDING_SIGNATURE", "Pending signature", true, false)));

        clauses = List.of(
                new ContractClause("v1.0", (short) 1, "Objeto", "Contenido objeto", true),
                new ContractClause("v1.0", (short) 2, "Uso", "Contenido uso", true)
        );
        when(clauseService.getOrSeedDefaultClauses()).thenReturn(clauses);
    }

    @Test
    void generateContract_Success_EmitsContractWithClausesAndConsecutive() {
        GenerateContractRequestDTO request = new GenerateContractRequestDTO(reservationId);

        when(rentalContractRepository.existsByReservationId(reservation.getId())).thenReturn(false);
        when(rentalContractRepository.countByContractNumberStartingWith(any())).thenReturn(0L);
        when(rentalContractRepository.save(any(RentalContract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractResponseDTO response = service.generateContract(request, customerEmail);

        assertThat(response).isNotNull();
        assertThat(response.contractNumber()).startsWith("CTR-");
        assertThat(response.reservationCode()).isEqualTo("RES-2026-0001");
        assertThat(response.statusCode()).isEqualTo("PENDING_SIGNATURE");
        assertThat(response.baseAmount()).isEqualByComparingTo("800000.00");
        assertThat(response.securityDeposit()).isEqualByComparingTo("1500000.00");
        assertThat(response.clauses()).hasSize(2);

        verify(rentalContractRepository).save(any(RentalContract.class));
    }

    @Test
    void generateContract_ThrowsConflict_WhenContractAlreadyExists() {
        GenerateContractRequestDTO request = new GenerateContractRequestDTO(reservationId);
        when(rentalContractRepository.existsByReservationId(reservation.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.generateContract(request, customerEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe un contrato legal emitido");

        verify(rentalContractRepository, never()).save(any());
    }

    @Test
    void generateContract_ThrowsConflict_WhenReservationCancelled() {
        ReservationStatus cancelledStatus = new ReservationStatus("CANCELLED", "Cancelled", false);
        reservation.setStatus(cancelledStatus);

        GenerateContractRequestDTO request = new GenerateContractRequestDTO(reservationId);
        when(rentalContractRepository.existsByReservationId(reservation.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.generateContract(request, customerEmail))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No es posible emitir un contrato para una reserva cancelada");
    }

    @Test
    void generateContract_ThrowsNotFound_WhenReservationNotFound() {
        UUID randomId = UUID.randomUUID();
        when(reservationRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateContract(new GenerateContractRequestDTO(randomId), customerEmail))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reserva no encontrada");
    }

    @Test
    void getContractById_Success() {
        RentalContract contract = new RentalContract(
                "CTR-2026-0001",
                reservation,
                customer,
                vehicle,
                draftStatus,
                branch,
                branch,
                reservation.getPickupDate(),
                reservation.getReturnDate(),
                reservation.getTotalEstimated(),
                new BigDecimal("1500000.00"),
                clauses
        );
        when(rentalContractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        ContractResponseDTO response = service.getContractById(contractId, customerEmail);

        assertThat(response.contractNumber()).isEqualTo("CTR-2026-0001");
        assertThat(response.customerFullName()).isEqualTo("Carlos Gomez");
    }

    @Test
    void getContractByReservationId_Success() {
        RentalContract contract = new RentalContract(
                "CTR-2026-0001",
                reservation,
                customer,
                vehicle,
                draftStatus,
                branch,
                branch,
                reservation.getPickupDate(),
                reservation.getReturnDate(),
                reservation.getTotalEstimated(),
                new BigDecimal("1500000.00"),
                clauses
        );
        when(rentalContractRepository.findByReservationId(reservationId)).thenReturn(Optional.of(contract));

        ContractResponseDTO response = service.getContractByReservationId(reservationId, customerEmail);

        assertThat(response.contractNumber()).isEqualTo("CTR-2026-0001");
    }

    @Test
    void getContractByNumber_Success() {
        RentalContract contract = new RentalContract(
                "CTR-2026-0001",
                reservation,
                customer,
                vehicle,
                draftStatus,
                branch,
                branch,
                reservation.getPickupDate(),
                reservation.getReturnDate(),
                reservation.getTotalEstimated(),
                new BigDecimal("1500000.00"),
                clauses
        );
        when(rentalContractRepository.findByContractNumber("CTR-2026-0001")).thenReturn(Optional.of(contract));

        ContractResponseDTO response = service.getContractByNumber("CTR-2026-0001", customerEmail);

        assertThat(response.contractNumber()).isEqualTo("CTR-2026-0001");
    }

    @Test
    void getContractByNumber_ThrowsNotFound() {
        when(rentalContractRepository.findByContractNumber("CTR-INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getContractByNumber("CTR-INVALID", customerEmail))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Contrato no encontrado");
    }
}
