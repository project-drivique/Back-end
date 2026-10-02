package com.drivique.api;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev") @AutoConfigureMockMvc
class PricingCatalogIntegrationTests extends DatabaseHealthTestSupport {
    @Autowired MockMvc mvc;
    @BeforeEach void reset() { resetRentalTables(); jdbc.execute("delete from catalog.additional_services"); jdbc.execute("delete from catalog.insurance_coverages"); }
    @Test void exposesActivePricingCatalogsPublicly() throws Exception {
        jdbc.update("insert into catalog.additional_services(id,name,daily_rate,is_active) values(?,?,?,true)", UUID.randomUUID(), "GPS Navigation", 18000);
        jdbc.update("insert into catalog.insurance_coverages(id,name,daily_rate,description,is_active) values(?,?,?,?,true)", UUID.randomUUID(), "Basic Protection", 0, "Included protection");
        mvc.perform(get("/api/v1/additional-services").contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$[0].dailyRate").value(18000));
        mvc.perform(get("/api/v1/insurance-coverages").contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("Included protection"));
    }
    @Test @WithMockUser(roles = "SUPER_ADMIN") void adminCreatesAndUpdatesPricingCatalogs() throws Exception {
        String serviceId = mvc.perform(post("/api/v1/admin/additional-services").contextPath("/api").contentType("application/json").content("{\"name\":\"Baby Seat\",\"dailyRate\":25000}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        mvc.perform(put("/api/v1/admin/additional-services/{id}", serviceId).contextPath("/api").contentType("application/json").content("{\"name\":\"Baby Seat\",\"dailyRate\":30000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyRate").value(30000));
        mvc.perform(post("/api/v1/admin/insurance-coverages").contextPath("/api").contentType("application/json").content("{\"name\":\"Premium\",\"dailyRate\":80000,\"description\":\"Full coverage\"}"))
                .andExpect(status().isCreated());
    }
    @Test void anonymousUserCannotConfigureRates() throws Exception {
        mvc.perform(post("/api/v1/admin/additional-services").contextPath("/api").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
