package com.drivique.api;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class FeaturesAndFavoritesIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private UserFavoriteVehicleRepository favoriteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleBrandRepository brandRepository;

    @Autowired
    private VehicleCategoryRepository categoryRepository;

    @Autowired
    private TransmissionTypeRepository transmissionRepository;

    @Autowired
    private FuelTypeRepository fuelRepository;

    @Autowired
    private VehicleStatusRepository statusRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private User testUser;
    private Vehicle testVehicle;
    private Feature featureStandard;
    private Feature featureTech;
    private Feature featureSafety;

    @BeforeEach
    void setup() {
        favoriteRepository.deleteAll();
        resetFleetTables();
        resetIamTables();
        resetLocationTables();

        testUser = userRepository.saveAndFlush(new User("Carlos", "Gomez", "customer@drivique.com", "hashedpassword"));

        Department dep = departmentRepository.saveAndFlush(new Department("Antioquia"));
        City city = cityRepository.saveAndFlush(new City(dep, "Medellín", true, true));
        Branch branch = branchRepository.saveAndFlush(new Branch("Sede El Poblado", "Carrera 43A", city, "3001234567", LocalTime.of(7, 0), LocalTime.of(21, 0), true));

        VehicleBrand brand = brandRepository.saveAndFlush(new VehicleBrand("Mazda"));
        VehicleCategory category = categoryRepository.saveAndFlush(new VehicleCategory("Sedan", new BigDecimal("120000.00"), new BigDecimal("1000000.00")));
        TransmissionType transmission = transmissionRepository.saveAndFlush(new TransmissionType("AUTOMATIC", "Automática"));
        FuelType fuel = fuelRepository.saveAndFlush(new FuelType("GASOLINE", "Gasolina"));
        VehicleStatus status = statusRepository.saveAndFlush(new VehicleStatus("AVAILABLE", "Disponible", true));

        testVehicle = vehicleRepository.saveAndFlush(new Vehicle(
                "XYZ-789", "3MZBM1V78KM123456", brand, category, transmission, fuel, status, branch,
                "Mazda 3 Touring", (short) 2024, "Rojo", (short) 5, (short) 4, 450, 8000, new BigDecimal("160000.00"), null, true
        ));

        featureStandard = featureRepository.saveAndFlush(new Feature("Air Conditioning", "STANDARD", "icon-ac"));
        featureTech = featureRepository.saveAndFlush(new Feature("Apple CarPlay & Android Auto", "TECHNOLOGY", "icon-carplay"));
        featureSafety = featureRepository.saveAndFlush(new Feature("Blind Spot Monitoring", "SECURITY", "icon-shield"));
    }

    @Test
    void publicFeaturesCatalogEndpoints() throws Exception {
        mvc.perform(get("/api/v1/features").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].group").exists())
                .andExpect(jsonPath("$[0].features").isArray());

        mvc.perform(get("/api/v1/features/all").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAssignAndRemoveFeaturesFromVehicle() throws Exception {
        String assignPayload = """
                {
                    "featureIds": ["%s", "%s"]
                }
                """.formatted(featureStandard.getId(), featureTech.getId());

        mvc.perform(post("/api/v1/admin/vehicles/{id}/features", testVehicle.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Public check vehicle features
        mvc.perform(get("/api/v1/vehicles/{id}/features", testVehicle.getId())
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Remove one feature
        mvc.perform(delete("/api/v1/admin/vehicles/{id}/features/{featureId}", testVehicle.getId(), featureStandard.getId())
                        .contextPath("/api"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/vehicles/{id}/features", testVehicle.getId())
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(featureTech.getId().toString()));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void userFavoritesLifecycle() throws Exception {
        // Initially empty
        mvc.perform(get("/api/v1/users/me/favorites").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Add favorite
        mvc.perform(post("/api/v1/users/me/favorites/{vehicleId}", testVehicle.getId()).contextPath("/api"))
                .andExpect(status().isCreated());

        // Idempotent add favorite
        mvc.perform(post("/api/v1/users/me/favorites/{vehicleId}", testVehicle.getId()).contextPath("/api"))
                .andExpect(status().isCreated());

        // Check favorite exists in list
        mvc.perform(get("/api/v1/users/me/favorites").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(testVehicle.getId().toString()))
                .andExpect(jsonPath("$[0].model").value("Mazda 3 Touring"))
                .andExpect(jsonPath("$[0].brandName").value("Mazda"));

        // Remove favorite
        mvc.perform(delete("/api/v1/users/me/favorites/{vehicleId}", testVehicle.getId()).contextPath("/api"))
                .andExpect(status().isNoContent());

        // Verify empty again
        mvc.perform(get("/api/v1/users/me/favorites").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unauthenticatedFavoritesAccessReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/users/me/favorites").contextPath("/api"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/users/me/favorites/{vehicleId}", testVehicle.getId()).contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }
}
