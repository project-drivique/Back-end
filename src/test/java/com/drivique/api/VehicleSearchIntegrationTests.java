package com.drivique.api;

import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class VehicleSearchIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private VehicleRepository vehicles;
    @Autowired private VehicleBrandRepository brands;
    @Autowired private VehicleCategoryRepository categories;
    @Autowired private TransmissionTypeRepository transmissions;
    @Autowired private FuelTypeRepository fuels;
    @Autowired private VehicleStatusRepository statuses;
    @Autowired private BranchRepository branches;
    @Autowired private CityRepository cities;
    @Autowired private DepartmentRepository departments;

    private Branch branchBogota;
    private Branch branchMedellin;
    private VehicleBrand brandToyota;
    private VehicleBrand brandMazda;
    private VehicleCategory categorySuv;
    private VehicleCategory categorySedan;
    private TransmissionType transAuto;
    private TransmissionType transManual;
    private FuelType fuelHybrid;
    private FuelType fuelGas;
    private VehicleStatus statusAvailable;
    private VehicleStatus statusMaintenance;

    @BeforeEach
    void setupData() {
        vehicles.deleteAll();
        branches.deleteAll();
        cities.deleteAll();
        departments.deleteAll();
        categories.deleteAll();
        brands.deleteAll();
        transmissions.deleteAll();
        fuels.deleteAll();
        statuses.deleteAll();

        Department dep = departments.saveAndFlush(new Department("Cundinamarca"));
        City cityBogota = cities.saveAndFlush(new City(dep, "Bogotá", true, true));
        City cityMed = cities.saveAndFlush(new City(dep, "Medellín", true, true));

        branchBogota = branches.saveAndFlush(new Branch("Sede Bogotá", "Calle 100", cityBogota, "111", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        branchMedellin = branches.saveAndFlush(new Branch("Sede Medellín", "Calle 10", cityMed, "222", LocalTime.of(8, 0), LocalTime.of(18, 0), true));

        brandToyota = brands.saveAndFlush(new VehicleBrand("Toyota"));
        brandMazda = brands.saveAndFlush(new VehicleBrand("Mazda"));

        categorySuv = categories.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("180000.00"), new BigDecimal("1500000.00")));
        categorySedan = categories.saveAndFlush(new VehicleCategory("Sedán", new BigDecimal("120000.00"), new BigDecimal("1000000.00")));

        transAuto = transmissions.saveAndFlush(new TransmissionType("AUTOMATIC", "Automatic"));
        transManual = transmissions.saveAndFlush(new TransmissionType("MANUAL", "Manual"));

        fuelHybrid = fuels.saveAndFlush(new FuelType("HYBRID", "Hybrid"));
        fuelGas = fuels.saveAndFlush(new FuelType("GASOLINE", "Gasoline"));

        statusAvailable = statuses.saveAndFlush(new VehicleStatus("AVAILABLE", "Available", true));
        statusMaintenance = statuses.saveAndFlush(new VehicleStatus("MAINTENANCE", "Maintenance", false));
    }

    @Test
    void searchFiltersByBranchCategoryAndPriceAndPaginates() throws Exception {
        // Vehicle 1: Toyota RAV4 in Bogota, SUV, Hybrid, Auto, 220,000, Featured
        vehicles.saveAndFlush(new Vehicle(
                "ABC-123", "VIN11111111111111", brandToyota, categorySuv, transAuto, fuelHybrid,
                statusAvailable, branchBogota, "RAV4 Hybrid", (short) 2024, "Blanco", (short) 5,
                (short) 5, 580, 10000, new BigDecimal("220000.00"), "http://img/rav4.jpg", true
        ));

        // Vehicle 2: Mazda CX-5 in Medellin, SUV, Gas, Auto, 240,000, Featured
        vehicles.saveAndFlush(new Vehicle(
                "DEF-456", "VIN22222222222222", brandMazda, categorySuv, transAuto, fuelGas,
                statusAvailable, branchMedellin, "CX-5 GT", (short) 2024, "Rojo", (short) 5,
                (short) 5, 506, 12000, new BigDecimal("240000.00"), "http://img/cx5.jpg", true
        ));

        // Vehicle 3: Sedan in Bogota, Non-featured, 120,000
        vehicles.saveAndFlush(new Vehicle(
                "GHI-789", "VIN33333333333333", brandToyota, categorySedan, transManual, fuelGas,
                statusAvailable, branchBogota, "Corolla", (short) 2023, "Gris", (short) 5,
                (short) 4, 470, 20000, new BigDecimal("120000.00"), "http://img/corolla.jpg", false
        ));

        // Vehicle 4: Maintenance vehicle (not reservable) -> should never appear
        vehicles.saveAndFlush(new Vehicle(
                "MAI-001", "VIN44444444444444", brandToyota, categorySuv, transAuto, fuelHybrid,
                statusMaintenance, branchBogota, "RAV4 Maint", (short) 2024, "Negro", (short) 5,
                (short) 5, 580, 15000, new BigDecimal("220000.00"), "http://img/maint.jpg", true
        ));

        // 1. Search all available
        mvc.perform(get("/api/v1/vehicles/search").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3));

        // 2. Filter by branch Bogota
        mvc.perform(get("/api/v1/vehicles/search")
                        .contextPath("/api")
                        .param("branchId", branchBogota.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 3. Filter by category SUV and price range
        mvc.perform(get("/api/v1/vehicles/search")
                        .contextPath("/api")
                        .param("categoryId", categorySuv.getId().toString())
                        .param("minPrice", "200000.00")
                        .param("maxPrice", "230000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].plate").value("ABC-123"))
                .andExpect(jsonPath("$.content[0].model").value("RAV4 Hybrid"));

        // 4. Test pagination size=1
        mvc.perform(get("/api/v1/vehicles/search")
                        .contextPath("/api")
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void featuredAndDetailEndpointsWorkCorrectly() throws Exception {
        Vehicle v = vehicles.saveAndFlush(new Vehicle(
                "FEA-100", "VIN55555555555555", brandToyota, categorySuv, transAuto, fuelHybrid,
                statusAvailable, branchBogota, "Land Cruiser", (short) 2024, "Negro", (short) 7,
                (short) 5, 800, 5000, new BigDecimal("500000.00"), "http://img/lc.jpg", true
        ));

        // Featured endpoint
        mvc.perform(get("/api/v1/vehicles/featured").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].model").value("Land Cruiser"))
                .andExpect(jsonPath("$[0].isFeatured").value(true));

        // Detail endpoint
        mvc.perform(get("/api/v1/vehicles/{id}", v.getId()).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("FEA-100"))
                .andExpect(jsonPath("$.brandName").value("Toyota"))
                .andExpect(jsonPath("$.categoryName").value("SUV"));

        // Non-existent detail returns 404
        mvc.perform(get("/api/v1/vehicles/{id}", UUID.randomUUID()).contextPath("/api"))
                .andExpect(status().isNotFound());
    }
}
