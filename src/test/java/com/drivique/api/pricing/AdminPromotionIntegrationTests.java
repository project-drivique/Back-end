package com.drivique.api.pricing;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AdminPromotionIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PromotionRepository promotions;

    @BeforeEach
    void setup() {
        promotions.deleteAll();
    }

    private String payload(String code, LocalDate start, LocalDate end) {
        return """
                {
                  "code": "%s", "name": "Promo de prueba", "conditions": "Condiciones de prueba",
                  "offerType": "COUPON", "discountType": "PERCENTAGE", "discountValue": 15,
                  "startDate": "%s", "endDate": "%s", "minimumAmount": 100000,
                  "audience": "ALL", "featured": true, "active": true
                }
                """.formatted(code, start, end);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void adminCreatesUpdatesTogglesAndDeletesPromotion() throws Exception {
        LocalDate today = LocalDate.now();
        mvc.perform(post("/v1/admin/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("verano15", today, today.plusDays(10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("VERANO15"))
                .andExpect(jsonPath("$.name").value("Promo de prueba"))
                .andExpect(jsonPath("$.featured").value(true));

        mvc.perform(post("/v1/admin/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("VERANO15", today, today.plusDays(10))))
                .andExpect(status().isConflict());

        mvc.perform(get("/v1/admin/promotions")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        String id = promotions.findByCodeIgnoreCase("VERANO15").orElseThrow().getId().toString();

        mvc.perform(put("/v1/admin/promotions/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(payload("VERANO15", today, today.plusDays(20)).replace("\"discountValue\": 15", "\"discountValue\": 25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountValue").value(25));

        mvc.perform(patch("/v1/admin/promotions/" + id + "/active")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(patch("/v1/admin/promotions/" + id + "/featured")).andExpect(status().isOk()).andExpect(jsonPath("$.featured").value(false));

        mvc.perform(delete("/v1/admin/promotions/" + id)).andExpect(status().isNoContent());
        assertThat(promotions.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void rejectsInvalidDatesAndPercentage() throws Exception {
        LocalDate today = LocalDate.now();
        mvc.perform(post("/v1/admin/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("BADDATES", today, today.minusDays(1))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/admin/promotions").contentType(MediaType.APPLICATION_JSON)
                        .content(payload("BADPCT", today, today.plusDays(1)).replace("\"discountValue\": 15", "\"discountValue\": 150")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotManagePromotions() throws Exception {
        mvc.perform(get("/v1/admin/promotions")).andExpect(status().isForbidden());
    }

    @Test
    void publishedListShowsOnlyCurrentActivePromotionsWithoutLogin() throws Exception {
        LocalDate today = LocalDate.now();
        promotions.save(new com.drivique.api.model.Promotion("VIGENTE", "COUPON", "PERCENTAGE", new java.math.BigDecimal("10"),
                java.time.Instant.now().minusSeconds(3600), java.time.Instant.now().plusSeconds(86400), 1, null, 0));
        promotions.save(new com.drivique.api.model.Promotion("VENCIDA", "COUPON", "PERCENTAGE", new java.math.BigDecimal("10"),
                java.time.Instant.now().minusSeconds(86400), java.time.Instant.now().minusSeconds(3600), 1, null, 0));
        assertThat(today).isNotNull();

        mvc.perform(get("/v1/promotions/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("VIGENTE"));
    }
}
