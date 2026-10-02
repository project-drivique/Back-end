package com.drivique.api;

import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class MaintenanceIntegrationTests extends DatabaseHealthTestSupport {
    @Autowired MockMvc mvc;
    @Autowired VehicleRepository vehicles;
    @Autowired VehicleBrandRepository brands;
    @Autowired VehicleCategoryRepository categories;
    @Autowired TransmissionTypeRepository transmissions;
    @Autowired FuelTypeRepository fuels;
    @Autowired VehicleStatusRepository statuses;
    @Autowired BranchRepository branches;
    @Autowired CityRepository cities;
    @Autowired DepartmentRepository departments;
    @Autowired MaintenanceTypeRepository types;
    private Vehicle vehicle;
    private MaintenanceType preventive;

    @BeforeEach
    void setup() {
        resetIamTables(); resetFleetTables(); resetLocationTables();
        Department department = departments.saveAndFlush(new Department("Cundinamarca"));
        City city = cities.saveAndFlush(new City(department, "Bogotá", true, true));
        Branch branch = branches.saveAndFlush(new Branch("Sede Centro", "Calle 1", city, "123", java.time.LocalTime.of(8, 0), java.time.LocalTime.of(18, 0), true));
        VehicleBrand brand = brands.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categories.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("200000"), new BigDecimal("500000")));
        TransmissionType transmission = transmissions.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        FuelType fuel = fuels.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));
        VehicleStatus available = statuses.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));
        statuses.saveAndFlush(new VehicleStatus("MAINTENANCE", "Maintenance", false));
        preventive = types.saveAndFlush(new MaintenanceType("PREVENTIVE", "Preventive"));
        vehicle = vehicles.saveAndFlush(new Vehicle("ABC-123", "1HGCR2F83HA000001", brand, category, transmission, fuel, available, branch, "RAV4", (short) 2024, "White", (short) 5, (short) 5, 580, 12000, new BigDecimal("220000"), null, false));
    }

    @Test
    @WithMockUser(authorities = "fleet:manage")
    void schedulesCompletesAndListsMaintenance() throws Exception {
        String scheduled = LocalDate.now().plusDays(1).toString();
        String request = "{\"vehicleId\":\"" + vehicle.getId() + "\",\"maintenanceTypeId\":\"" + preventive.getId() + "\",\"scheduledDate\":\"" + scheduled + "\",\"cost\":250000,\"description\":\"Oil change\"}";
        String id = mvc.perform(post("/api/v1/admin/maintenances").contextPath("/api").contentType("application/json").content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.maintenanceTypeCode").value("PREVENTIVE"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        org.assertj.core.api.Assertions.assertThat(vehicleStatus()).isEqualTo("MAINTENANCE");
        mvc.perform(get("/api/v1/admin/vehicles/{id}/maintenances", vehicle.getId()).contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/v1/admin/maintenances/{id}/complete", id).contextPath("/api").contentType("application/json")
                        .content("{\"completedDate\":\"" + scheduled + "\",\"cost\":275000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cost").value(275000));
        org.assertj.core.api.Assertions.assertThat(vehicleStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    void anonymousUserCannotScheduleMaintenance() throws Exception {
        mvc.perform(post("/api/v1/admin/maintenances").contextPath("/api").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String vehicleStatus() {
        return jdbc.queryForObject("select vs.code from fleet.vehicles v join fleet.vehicle_statuses vs on vs.id = v.status_id where v.id = ?", String.class, vehicle.getId());
    }
}
