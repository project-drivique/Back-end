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
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class VehicleAssetsIntegrationTests extends DatabaseHealthTestSupport {

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

    @Autowired
    private VehicleImageRepository imageRepository;

    @Autowired
    private VehicleDocumentRepository documentRepository;

    private Vehicle vehicle;

    @BeforeEach
    void setup() {
        documentRepository.deleteAll();
        imageRepository.deleteAll();
        resetFleetTables();
        resetLocationTables();

        Department dep = departments.saveAndFlush(new Department("Cundinamarca"));
        City city = cities.saveAndFlush(new City(dep, "Bogotá", true, true));
        Branch branch = branches.saveAndFlush(new Branch("Sede Bogotá", "Calle 100", city, "111", LocalTime.of(8, 0), LocalTime.of(18, 0), true));

        VehicleBrand brand = brands.saveAndFlush(new VehicleBrand("Toyota"));
        VehicleCategory category = categories.saveAndFlush(new VehicleCategory("SUV", new BigDecimal("180000.00"), new BigDecimal("1500000.00")));
        TransmissionType transmission = transmissions.saveAndFlush(new TransmissionType("AUTOMATIC", "Automática"));
        FuelType fuel = fuels.saveAndFlush(new FuelType("HYBRID", "Híbrido"));
        VehicleStatus status = statuses.saveAndFlush(new VehicleStatus("AVAILABLE", "Disponible", true));

        vehicle = vehicles.saveAndFlush(new Vehicle(
                "ABC-123", "1HGCR2F83HA000001", brand, category, transmission, fuel, status, branch,
                "RAV4 Hybrid", (short) 2024, "Blanco", (short) 5, (short) 5, 580, 15000, new BigDecimal("220000.00"), null, true
        ));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void manageVehicleImagesFlow() throws Exception {
        // 1. Add first image (primary)
        String img1 = """
                {
                    "url": "https://images.unsplash.com/photo-front",
                    "isPrimary": true,
                    "sortOrder": 1
                }
                """;

        mvc.perform(post("/api/v1/admin/vehicles/{id}/images", vehicle.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(img1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://images.unsplash.com/photo-front"))
                .andExpect(jsonPath("$.isPrimary").value(true))
                .andExpect(jsonPath("$.sortOrder").value(1));

        // 2. Add second image (non-primary)
        String img2 = """
                {
                    "url": "https://images.unsplash.com/photo-interior",
                    "isPrimary": false,
                    "sortOrder": 2
                }
                """;

        var res2 = mvc.perform(post("/api/v1/admin/vehicles/{id}/images", vehicle.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(img2))
                .andExpect(status().isCreated())
                .andReturn();

        String image2Id = com.jayway.jsonpath.JsonPath.read(res2.getResponse().getContentAsString(), "$.id");

        // 3. Duplicate sortOrder fails with 409
        String duplicateOrder = """
                {
                    "url": "https://images.unsplash.com/photo-dup",
                    "isPrimary": false,
                    "sortOrder": 1
                }
                """;

        mvc.perform(post("/api/v1/admin/vehicles/{id}/images", vehicle.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateOrder))
                .andExpect(status().isConflict());

        // 4. List images
        mvc.perform(get("/api/v1/admin/vehicles/{id}/images", vehicle.getId())
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sortOrder").value(1))
                .andExpect(jsonPath("$[1].sortOrder").value(2));

        // 5. Change primary image to image2
        mvc.perform(patch("/api/v1/admin/vehicles/{id}/images/{imageId}/primary", vehicle.getId(), image2Id)
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrimary").value(true));

        Vehicle reloaded = vehicles.findById(vehicle.getId()).orElseThrow();
        assertThat(reloaded.getMainImageUrl()).isEqualTo("https://images.unsplash.com/photo-interior");

        // 6. Delete image
        mvc.perform(delete("/api/v1/admin/vehicles/{id}/images/{imageId}", vehicle.getId(), image2Id)
                        .contextPath("/api"))
                .andExpect(status().isNoContent());

        assertThat(imageRepository.existsById(java.util.UUID.fromString(image2Id))).isFalse();
    }

    @Test
    @WithMockUser(authorities = "fleet:manage")
    void manageVehicleDocumentsAndExpiringAlerts() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate expiringDate = today.plusDays(15); // <= 30 days, should trigger alert

        String docPayload = """
                {
                    "documentType": "SOAT",
                    "documentNumber": "SOAT-2024-999",
                    "fileUrl": "https://docs.drivique.com/soat.pdf",
                    "issuedAt": "%s",
                    "expiresAt": "%s"
                }
                """.formatted(today.minusMonths(11), expiringDate);

        var docRes = mvc.perform(post("/api/v1/admin/vehicles/{id}/documents", vehicle.getId())
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(docPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("SOAT"))
                .andExpect(jsonPath("$.documentNumber").value("SOAT-2024-999"))
                .andExpect(jsonPath("$.isExpiringSoon").value(true))
                .andExpect(jsonPath("$.daysUntilExpiration").value(15))
                .andReturn();

        String docId = com.jayway.jsonpath.JsonPath.read(docRes.getResponse().getContentAsString(), "$.id");

        // Check vehicle expiring alert endpoint
        mvc.perform(get("/api/v1/admin/vehicles/{id}/documents/expiring", vehicle.getId())
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documentNumber").value("SOAT-2024-999"));

        // Check global expiring alert endpoint
        mvc.perform(get("/api/v1/admin/vehicles/documents/expiring")
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].documentType").value("SOAT"));

        // Soft delete document
        mvc.perform(delete("/api/v1/admin/vehicles/{id}/documents/{docId}", vehicle.getId(), docId)
                        .contextPath("/api"))
                .andExpect(status().isNoContent());

        var document = documentRepository.findById(java.util.UUID.fromString(docId)).orElseThrow();
        assertThat(document.isActive()).isFalse();
    }

    @Test
    void unauthenticatedAccessReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/admin/vehicles/{id}/images", vehicle.getId()).contextPath("/api"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/admin/vehicles/{id}/documents", vehicle.getId()).contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }
}
