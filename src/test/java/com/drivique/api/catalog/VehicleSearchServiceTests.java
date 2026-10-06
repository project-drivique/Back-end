package com.drivique.api.catalog;

import com.drivique.api.dto.PageResponseDTO;
import com.drivique.api.dto.VehicleCardResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.VehicleRepository;
import com.drivique.api.service.VehicleSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleSearchServiceTests {

    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleSearchService vehicleSearchService;

    @BeforeEach
    void setUp() {
        vehicleSearchService = new VehicleSearchService(vehicleRepository);
    }

    private Vehicle createMockVehicle(UUID id, String model) {
        Department dept = new Department("Cundinamarca");
        City city = new City(dept, "Bogota", true, true);
        Branch branch = new Branch("Sede Principal", "Calle 100", city, "3001234567",
                LocalTime.of(8, 0), LocalTime.of(18, 0), true);

        VehicleBrand brand = new VehicleBrand("Toyota");
        VehicleCategory category = new VehicleCategory("SUV", new BigDecimal("150000.00"), new BigDecimal("500000.00"));
        TransmissionType transmission = new TransmissionType("AUTOMATIC", "Automatica");
        FuelType fuel = new FuelType("GASOLINE", "Gasolina");
        VehicleStatus status = new VehicleStatus("AVAILABLE", "Disponible", true);

        Vehicle vehicle = new Vehicle(
                "ABC-123",
                "1HGCR2F83HA000000",
                brand,
                category,
                transmission,
                fuel,
                status,
                branch,
                model,
                (short) 2024,
                "Blanco",
                (short) 5,
                (short) 4,
                450,
                15000,
                new BigDecimal("150000.00"),
                "https://images.unsplash.com/photo-1549399542-7e3f8b79c341",
                true
        );
        ReflectionTestUtils.setField(vehicle, "id", id);
        return vehicle;
    }

    @Test
    void searchReturnsPagedVehicleCardDTOs() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = createMockVehicle(vehicleId, "Corolla Cross");
        Page<Vehicle> page = new PageImpl<>(List.of(vehicle), PageRequest.of(0, 10), 1);

        when(vehicleRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponseDTO<VehicleCardResponseDTO> result = vehicleSearchService.search(
                null, null, null, null, null, null, null, PageRequest.of(0, 10)
        );

        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).model()).isEqualTo("Corolla Cross");
        assertThat(result.content().get(0).brandName()).isEqualTo("Toyota");
    }

    @Test
    void featuredReturnsOnlyFeaturedVehicles() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = createMockVehicle(vehicleId, "Fortuner");

        when(vehicleRepository.findFeaturedVehicles()).thenReturn(List.of(vehicle));

        List<VehicleCardResponseDTO> featured = vehicleSearchService.featured();

        assertThat(featured).hasSize(1);
        assertThat(featured.get(0).model()).isEqualTo("Fortuner");
    }

    @Test
    void detailReturnsVehicleWhenFound() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = createMockVehicle(vehicleId, "RAV4");

        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicle));

        VehicleCardResponseDTO dto = vehicleSearchService.detail(vehicleId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(vehicleId);
        assertThat(dto.model()).isEqualTo("RAV4");
    }

    @Test
    void detailThrowsNotFoundWhenVehicleNotExists() {
        UUID nonExistent = UUID.randomUUID();
        when(vehicleRepository.findByIdAndActiveTrue(nonExistent)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleSearchService.detail(nonExistent))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Vehicle not found");
    }
}
