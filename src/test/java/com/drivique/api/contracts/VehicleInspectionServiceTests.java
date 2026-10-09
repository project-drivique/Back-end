package com.drivique.api.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.drivique.api.dto.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.drivique.api.service.FileStorageService;
import com.drivique.api.service.VehicleInspectionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VehicleInspectionServiceTests {
    private final RentalContractRepository contracts = mock(RentalContractRepository.class);
    private final VehicleInspectionRepository inspections = mock(VehicleInspectionRepository.class);
    private final InspectionChecklistItemRepository checklistItems = mock(InspectionChecklistItemRepository.class);
    private final InspectionChecklistAnswerRepository answers = mock(InspectionChecklistAnswerRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ContractStatusRepository contractStatuses = mock(ContractStatusRepository.class);
    private final ReservationStatusRepository reservationStatuses = mock(ReservationStatusRepository.class);
    private final VehicleInspectionService service = new VehicleInspectionService(contracts, inspections, checklistItems, answers, users, storage, contractStatuses, reservationStatuses, new BigDecimal("5000"), new BigDecimal("200000"));

    @Test
    void checkoutCalculatesExtraMileageAndUpdatesVehicleMileage() {
        UUID contractId = UUID.randomUUID(); UUID itemId = UUID.randomUUID();
        RentalContract contract = mock(RentalContract.class); Vehicle vehicle = mock(Vehicle.class); Reservation reservation = mock(Reservation.class); MileagePlan plan = mock(MileagePlan.class);
        User inspector = mock(User.class); Role role=mock(Role.class); InspectionChecklistItem item = mock(InspectionChecklistItem.class); VehicleInspection checkIn = mock(VehicleInspection.class);
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract)); when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("staff@drivique.com")).thenReturn(Optional.of(inspector));
        when(inspections.existsByContractIdAndInspectionType(contractId, "CHECK_OUT")).thenReturn(false); when(inspections.findByContractIdAndInspectionType(contractId, "CHECK_IN")).thenReturn(Optional.of(checkIn));
        when(checkIn.getMileage()).thenReturn(1_000); when(checkIn.getFuelLevelPercent()).thenReturn(new BigDecimal("80.00"));
        when(contract.getSignedAt()).thenReturn(java.time.Instant.now()); when(role.getCode()).thenReturn("ADMIN"); when(inspector.getRoles()).thenReturn(java.util.Set.of(role));
        when(contractStatuses.findByCodeIgnoreCase("FINALIZED")).thenReturn(Optional.of(mock(ContractStatus.class))); when(reservationStatuses.findByCodeIgnoreCase("COMPLETED")).thenReturn(Optional.of(mock(ReservationStatus.class)));
        when(contract.getVehicle()).thenReturn(vehicle); when(vehicle.getMileage()).thenReturn(1_000); when(contract.getReservation()).thenReturn(reservation); when(reservation.getMileagePlan()).thenReturn(plan);
        when(plan.getIncludedKm()).thenReturn(100); when(plan.getExtraKmRate()).thenReturn(new BigDecimal("500.00"));
        when(checklistItems.findById(itemId)).thenReturn(Optional.of(item)); when(item.isActive()).thenReturn(true); when(item.getId()).thenReturn(itemId); when(item.getName()).thenReturn("Carrocería");
        when(inspections.saveAndFlush(any(VehicleInspection.class))).thenAnswer(invocation -> invocation.getArgument(0));
        VehicleInspectionRequestDTO request = new VehicleInspectionRequestDTO("CHECK_OUT", 1_150, new BigDecimal("65.00"), null, List.of(new InspectionChecklistAnswerRequestDTO(itemId, true, null, null)));

        VehicleInspectionResponseDTO response = service.register(contractId, request, List.of(), "staff@drivique.com");

        assertThat(response.mileageDifference()).isEqualTo(150); assertThat(response.fuelLevelDifference()).isEqualByComparingTo("15.00"); assertThat(response.extraMileageCharge()).isEqualByComparingTo("25000.00");
        verify(vehicle).setMileage(1_150);
    }

    @Test
    void rejectsNonCompliantItemWithoutPhotoEvidence() {
        UUID contractId = UUID.randomUUID(); UUID itemId = UUID.randomUUID();
        RentalContract contract = mock(RentalContract.class); Vehicle vehicle = mock(Vehicle.class); User inspector = mock(User.class); Role role=mock(Role.class); InspectionChecklistItem item = mock(InspectionChecklistItem.class);
        when(contracts.findById(contractId)).thenReturn(Optional.of(contract)); when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("staff@drivique.com")).thenReturn(Optional.of(inspector));
        when(inspections.existsByContractIdAndInspectionType(contractId, "CHECK_IN")).thenReturn(false); when(contract.getVehicle()).thenReturn(vehicle); when(vehicle.getMileage()).thenReturn(0);
        when(contract.getSignedAt()).thenReturn(java.time.Instant.now()); when(role.getCode()).thenReturn("ADMIN"); when(inspector.getRoles()).thenReturn(java.util.Set.of(role));
        when(checklistItems.findById(itemId)).thenReturn(Optional.of(item)); when(item.isActive()).thenReturn(true);
        VehicleInspectionRequestDTO request = new VehicleInspectionRequestDTO("CHECK_IN", 10, new BigDecimal("90.00"), null, List.of(new InspectionChecklistAnswerRequestDTO(itemId, false, "Rayón visible", null)));

        assertThatThrownBy(() -> service.register(contractId, request, List.of(), "staff@drivique.com"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("foto de evidencia");
    }
}
