package com.drivique.api.service;

import com.drivique.api.dto.DeliveryPointRequestDTO;
import com.drivique.api.dto.DeliveryPointResponseDTO;
import org.springframework.security.access.AccessDeniedException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.CityRepository;
import com.drivique.api.repository.ReservationDeliveryPointRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationDeliveryPointServiceTests {

    private final ReservationDeliveryPointRepository deliveryPointRepository = mock(ReservationDeliveryPointRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final CityRepository cityRepository = mock(CityRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final ReservationDeliveryPointService service = new ReservationDeliveryPointService(
            deliveryPointRepository,
            reservationRepository,
            branchRepository,
            cityRepository,
            userRepository
    );

    private final UUID reservationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final String userEmail = "customer@drivique.com";

    private Reservation reservation;
    private User user;
    private Branch branch;
    private City city;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn(userEmail);
        when(user.getRoles()).thenReturn(Set.of());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(user));

        reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(reservationId);
        when(reservation.getCustomer()).thenReturn(user);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        branch = mock(Branch.class);
        when(branch.getId()).thenReturn(branchId);
        when(branch.getName()).thenReturn("Sede El Poblado");
        when(branch.isActive()).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        city = mock(City.class);
        when(city.getId()).thenReturn(cityId);
        when(city.getName()).thenReturn("Medellín");
        when(city.isActive()).thenReturn(true);
        when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));

        when(deliveryPointRepository.save(any(ReservationDeliveryPoint.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void configureDeliveryPoints_BranchModality_Success() {
        DeliveryPointRequestDTO request = new DeliveryPointRequestDTO(
                "PICKUP",
                "BRANCH",
                branchId,
                null,
                null,
                null,
                null,
                "Entregar en módulo 3"
        );

        when(deliveryPointRepository.findByReservationAndPointType(reservation, "PICKUP"))
                .thenReturn(Optional.empty());

        List<DeliveryPointResponseDTO> result = service.configureDeliveryPoints(reservationId, List.of(request), userEmail);

        assertThat(result).hasSize(1);
        DeliveryPointResponseDTO dto = result.get(0);
        assertThat(dto.pointType()).isEqualTo("PICKUP");
        assertThat(dto.modality()).isEqualTo("BRANCH");
        assertThat(dto.branchId()).isEqualTo(branchId);
        assertThat(dto.branchName()).isEqualTo("Sede El Poblado");
        assertThat(dto.referenceDetails()).isEqualTo("Entregar en módulo 3");
    }

    @Test
    void configureDeliveryPoints_HomeDeliveryModality_Success() {
        DeliveryPointRequestDTO request = new DeliveryPointRequestDTO(
                "RETURN",
                "HOME_DELIVERY",
                null,
                cityId,
                "El Poblado",
                "Calle 10 # 43E-20 Apto 502",
                null,
                "Dejar llaves en portería"
        );

        when(deliveryPointRepository.findByReservationAndPointType(reservation, "RETURN"))
                .thenReturn(Optional.empty());

        List<DeliveryPointResponseDTO> result = service.configureDeliveryPoints(reservationId, List.of(request), userEmail);

        assertThat(result).hasSize(1);
        DeliveryPointResponseDTO dto = result.get(0);
        assertThat(dto.pointType()).isEqualTo("RETURN");
        assertThat(dto.modality()).isEqualTo("HOME_DELIVERY");
        assertThat(dto.cityId()).isEqualTo(cityId);
        assertThat(dto.cityName()).isEqualTo("Medellín");
        assertThat(dto.neighborhood()).isEqualTo("El Poblado");
        assertThat(dto.address()).isEqualTo("Calle 10 # 43E-20 Apto 502");
        assertThat(dto.referenceDetails()).isEqualTo("Dejar llaves en portería");
    }

    @Test
    void configureDeliveryPoints_AirportAndTerminalModality_Success() {
        DeliveryPointRequestDTO pickupAirport = new DeliveryPointRequestDTO(
                "PICKUP",
                "AIRPORT",
                null,
                cityId,
                null,
                null,
                "AV9301",
                "Puerta 4 llegadas nacionales"
        );

        DeliveryPointRequestDTO returnTerminal = new DeliveryPointRequestDTO(
                "RETURN",
                "TERMINAL",
                null,
                cityId,
                null,
                null,
                "BOLIV-405",
                "Bahía 12 Terminal del Norte"
        );

        when(deliveryPointRepository.findByReservationAndPointType(reservation, "PICKUP"))
                .thenReturn(Optional.empty());
        when(deliveryPointRepository.findByReservationAndPointType(reservation, "RETURN"))
                .thenReturn(Optional.empty());

        List<DeliveryPointResponseDTO> result = service.configureDeliveryPoints(
                reservationId,
                List.of(pickupAirport, returnTerminal),
                userEmail
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).modality()).isEqualTo("AIRPORT");
        assertThat(result.get(0).flightOrBusNumber()).isEqualTo("AV9301");
        assertThat(result.get(1).modality()).isEqualTo("TERMINAL");
        assertThat(result.get(1).flightOrBusNumber()).isEqualTo("BOLIV-405");
    }

    @Test
    void configureDeliveryPoints_MissingBranchId_ThrowsException() {
        DeliveryPointRequestDTO invalid = new DeliveryPointRequestDTO(
                "PICKUP",
                "BRANCH",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.configureDeliveryPoints(reservationId, List.of(invalid), userEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("branchId es obligatorio");
    }

    @Test
    void configureDeliveryPoints_MissingHomeDeliveryAddress_ThrowsException() {
        DeliveryPointRequestDTO invalid = new DeliveryPointRequestDTO(
                "RETURN",
                "HOME_DELIVERY",
                null,
                cityId,
                "Laureles",
                "",
                null,
                null
        );

        assertThatThrownBy(() -> service.configureDeliveryPoints(reservationId, List.of(invalid), userEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dirección es obligatoria");
    }

    @Test
    void configureDeliveryPoints_UnauthorizedUser_ThrowsAccessDenied() {
        User otherUser = mock(User.class);
        when(otherUser.getId()).thenReturn(UUID.randomUUID());
        when(otherUser.getRoles()).thenReturn(Set.of());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("intruder@drivique.com")).thenReturn(Optional.of(otherUser));

        DeliveryPointRequestDTO request = new DeliveryPointRequestDTO(
                "PICKUP",
                "BRANCH",
                branchId,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.configureDeliveryPoints(reservationId, List.of(request), "intruder@drivique.com"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene permisos");
    }

    @Test
    void getDeliveryPointsByReservation_Success() {
        ReservationDeliveryPoint p1 = new ReservationDeliveryPoint(
                reservation,
                "PICKUP",
                "BRANCH",
                branch,
                null,
                null,
                null,
                null,
                null
        );

        when(deliveryPointRepository.findByReservation(reservation)).thenReturn(List.of(p1));

        List<DeliveryPointResponseDTO> result = service.getDeliveryPointsByReservation(reservationId, userEmail);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pointType()).isEqualTo("PICKUP");
        assertThat(result.get(0).modality()).isEqualTo("BRANCH");
    }
}
