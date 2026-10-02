package com.drivique.api.fleet;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AdminVehicleIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private VehicleBrandRepository brands;

    @Autowired
    private VehicleCategoryRepository categories;

    @Autowired
    private TransmissionTypeRepository transmissions;

    @Autowired
    private FuelTypeRepository fuels;

    @Autowired
    private VehicleStatusRepository statuses;

    @Autowired
    private BranchRepository branches;

    @Autowired
    private CityRepository cities;

    @Autowired
    private DepartmentRepository departments;

    private VehicleBrand brand;
    private VehicleCategory category;
    private TransmissionType transmission;
    private FuelType fuel;
    private VehicleStatus statusAvailable;
    private VehicleStatus statusMaintenance;
    private Branch branch;

    @BeforeEach
    void setup() {
        resetFleetTables();
        resetLocationTables();

        Department dep = departments.saveAndFlush(new Department("Cundinamarca"));
        City city = cities.saveAndFlush(new City(dep, "Bogotá", true, true));
        branch = branches.saveAndFlush(new Branch("Sede Principal", "Calle 100 # 15-20", city, "3001234567", LocalTime.of(8, 0), LocalTime.of(18, 0), true));

        brand = brands.saveAndFlush(new VehicleBrand("Toyota"));
        category = categories.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("180000.00"), new BigDecimal("1500000.00")));
        transmission = transmissions.saveAndFlush(new TransmissionType("AUTOMATIC", "Automática"));
        fuel = fuels.saveAndFlush(new FuelType("HYBRID", "Híbrido"));
        statusAvailable = statuses.saveAndFlush(new VehicleStatus("AVAILABLE", "Disponible", true));
        statusMaintenance = statuses.saveAndFlush(new VehicleStatus("MAINTENANCE", "Mantenimiento", false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createVehicleSuccessfully() throws Exception {
        String payload = """
                {
                    "plate": "XYZ-789",
                    "vin": "1HGCR2F83HA000999",
                    "brandId": "%s",
                    "categoryId": "%s",
                    "transmissionTypeId": "%s",
                    "fuelTypeId": "%s",
                    "currentBranchId": "%s",
                    "model": "Corolla Cross XEI",
                    "year": 2024,
                    "color": "Plata",
                    "passengerCapacity": 5,
                    "doorsCount": 5,
                    "trunkCapacityLiters": 440,
                    "mileage": 5000,
                    "dailyRate": 195000.00,
                    "mainImageUrl": "https://images.unsplash.com/photo-corolla",
                    "isFeatured": true
                }
                """.formatted(
                brand.getId(),
                category.getId(),
                transmission.getId(),
                fuel.getId(),
                branch.getId()
        );

        mvc.perform(post("/api/v1/admin/vehicles")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plate").value("XYZ-789"))
                .andExpect(jsonPath("$.vin").value("1HGCR2F83HA000999"))
                .andExpect(jsonPath("$.brandName").value("Toyota"))
                .andExpect(jsonPath("$.categoryName").value("SUV"))
                .andExpect(jsonPath("$.model").value("Corolla Cross XEI"))
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.dailyRate").value(195000.00))
                .andExpect(jsonPath("$.isFeatured").value(true))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(authorities = "fleet:manage")
    void createVehicleWithDuplicatePlateOrVinFails() throws Exception {
        vehicles.saveAndFlush(new Vehicle(
                "XYZ-789", "1HGCR2F83HA000999", brand, category, transmission, fuel, statusAvailable, branch,
                "Corolla Cross", (short) 2024, "Plata", (short) 5, (short) 5, 440, 5000, new BigDecimal("195000.00"), null, false
        ));

        String duplicatePlatePayload = """
                {
                    "plate": "XYZ-789",
                    "vin": "2T1BURHE5HC000888",
                    "brandId": "%s",
                    "categoryId": "%s",
                    "transmissionTypeId": "%s",
                    "fuelTypeId": "%s",
                    "currentBranchId": "%s",
                    "model": "RAV4",
                    "year": 2024,
                    "passengerCapacity": 5,
                    "mileage": 1000,
                    "dailyRate": 220000.00
                }
                """.formatted(brand.getId(), category.getId(), transmission.getId(), fuel.getId(), branch.getId());

        mvc.perform(post("/api/v1/admin/vehicles")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatePlatePayload))
                .andExpect(status().isConflict());

        String duplicateVinPayload = """
                {
                    "plate": "NEW-123",
                    "vin": "1HGCR2F83HA000999",
                    "brandId": "%s",
                    "categoryId": "%s",
                    "transmissionTypeId": "%s",
                    "fuelTypeId": "%s",
                    "currentBranchId": "%s",
                    "model": "RAV4",
                    "year": 2024,
                    "passengerCapacity": 5,
                    "mileage": 1000,
                    "dailyRate": 220000.00
                }
                """.formatted(brand.getId(), category.getId(), transmission.getId(), fuel.getId(), branch.getId());

        mvc.perform(post("/api/v1/admin/vehicles")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateVinPayload))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateVehicleMileageAndBranch() throws Exception {
        Vehicle saved = vehicles.saveAndFlush(new Vehicle(
                "XYZ-789", "1HGCR2F83HA000999", brand, category, transmission, fuel, statusAvailable, branch,
                "Corolla Cross", (short) 2024, "Plata", (short) 5, (short) 5, 440, 5000, new BigDecimal("195000.00"), null, false
        ));

        // Valid update: mileage increased
        String validUpdate = """
                {
                    "mileage": 6200,
                    "dailyRate": 200000.00,
                    "currentBranchId": "%s",
                    "color": "Gris Oscuro",
                    "isFeatured": true
                }
                """.formatted(branch.getId());

        mvc.perform(put("/api/v1/admin/vehicles/{id}", saved.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(6200))
                .andExpect(jsonPath("$.dailyRate").value(200000.00))
                .andExpect(jsonPath("$.color").value("Gris Oscuro"))
                .andExpect(jsonPath("$.isFeatured").value(true));

        // Invalid update: mileage decreased
        String invalidMileageUpdate = """
                {
                    "mileage": 4000,
                    "dailyRate": 200000.00,
                    "currentBranchId": "%s"
                }
                """.formatted(branch.getId());

        mvc.perform(put("/api/v1/admin/vehicles/{id}", saved.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidMileageUpdate))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateVehicleStatusAndToggleActive() throws Exception {
        Vehicle saved = vehicles.saveAndFlush(new Vehicle(
                "XYZ-789", "1HGCR2F83HA000999", brand, category, transmission, fuel, statusAvailable, branch,
                "Corolla Cross", (short) 2024, "Plata", (short) 5, (short) 5, 440, 5000, new BigDecimal("195000.00"), null, false
        ));

        // Change status to MAINTENANCE
        String statusUpdate = """
                {
                    "statusCode": "MAINTENANCE"
                }
                """;

        mvc.perform(patch("/api/v1/admin/vehicles/{id}/status", saved.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("MAINTENANCE"))
                .andExpect(jsonPath("$.allowsReservation").value(false));

        // Toggle active status
        mvc.perform(patch("/api/v1/admin/vehicles/{id}/toggle-status", saved.getId())
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Soft delete endpoint
        mvc.perform(delete("/api/v1/admin/vehicles/{id}", saved.getId())
                        .contextPath("/api"))
                .andExpect(status().isNoContent());

        Vehicle updated = vehicles.findById(saved.getId()).orElseThrow();
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void unauthenticatedAccessReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/admin/vehicles").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }
}
