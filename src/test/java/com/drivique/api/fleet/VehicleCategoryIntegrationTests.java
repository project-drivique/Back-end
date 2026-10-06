package com.drivique.api.fleet;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.model.VehicleCategory;
import com.drivique.api.repository.VehicleCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class VehicleCategoryIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VehicleCategoryRepository categories;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetCategories() {
        resetFleetTables();
        var cache = cacheManager.getCache("vehicleCategories");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void publicEndpointsExposeActiveCategoriesAndDetail() throws Exception {
        VehicleCategory active = categories.saveAndFlush(
                new VehicleCategory("SUV", new BigDecimal("180000.00"), new BigDecimal("1500000.00"))
        );
        VehicleCategory inactive = categories.saveAndFlush(
                new VehicleCategory("Sedán", new BigDecimal("120000.00"), new BigDecimal("1000000.00"))
        );
        inactive.toggleStatus();
        categories.saveAndFlush(inactive);

        mvc.perform(get("/api/v1/vehicle-categories").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("SUV"))
                .andExpect(jsonPath("$[0].baseDailyRate").value(180000.00))
                .andExpect(jsonPath("$[0].securityDeposit").value(1500000.00));

        mvc.perform(get("/api/v1/vehicle-categories/{id}", active.getId()).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SUV"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void adminCreatesUpdatesTogglesAndRejectsDuplicatesAndNegativeRates() throws Exception {
        String payload = """
                {
                    "name": "Camioneta",
                    "baseDailyRate": 220000.00,
                    "securityDeposit": 2000000.00
                }
                """;

        mvc.perform(post("/api/v1/vehicle-categories")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Camioneta"))
                .andExpect(jsonPath("$.baseDailyRate").value(220000.00))
                .andExpect(jsonPath("$.securityDeposit").value(2000000.00))
                .andExpect(jsonPath("$.isActive").value(true));

        UUID id = categories.findAll().get(0).getId();

        // Duplicate name conflict
        mvc.perform(post("/api/v1/vehicle-categories")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isConflict());

        // Update category
        String updatePayload = """
                {
                    "name": "Camioneta 4x4",
                    "baseDailyRate": 250000.00,
                    "securityDeposit": 2500000.00
                }
                """;
        mvc.perform(put("/api/v1/vehicle-categories/{id}", id)
                        .contextPath("/api")
                        .contentType("application/json")
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camioneta 4x4"))
                .andExpect(jsonPath("$.baseDailyRate").value(250000.00));

        // Toggle status
        mvc.perform(patch("/api/v1/vehicle-categories/{id}/toggle-status", id)
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        // Negative base daily rate validation error
        String negativeRatePayload = """
                {
                    "name": "Hatchback",
                    "baseDailyRate": -50.00,
                    "securityDeposit": 800000.00
                }
                """;
        mvc.perform(post("/api/v1/vehicle-categories")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content(negativeRatePayload))
                .andExpect(status().isBadRequest());

        // Negative security deposit validation error
        String negativeDepositPayload = """
                {
                    "name": "Hatchback",
                    "baseDailyRate": 100000.00,
                    "securityDeposit": -100.00
                }
                """;
        mvc.perform(post("/api/v1/vehicle-categories")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content(negativeDepositPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotManageVehicleCategories() throws Exception {
        String payload = """
                {
                    "name": "Lujo",
                    "baseDailyRate": 350000.00,
                    "securityDeposit": 3000000.00
                }
                """;

        mvc.perform(post("/api/v1/vehicle-categories")
                        .contextPath("/api")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }
}
