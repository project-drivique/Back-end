package com.drivique.api.pricing;

import com.drivique.api.DatabaseHealthTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@ActiveProfiles("dev") @AutoConfigureMockMvc class MileagePlanIntegrationTests extends DatabaseHealthTestSupport {
 @Autowired MockMvc mvc;
 @BeforeEach void reset(){resetRentalTables(); jdbc.execute("delete from catalog.mileage_plans");}
 @Test void exposesActivePlansPublicly() throws Exception {jdbc.update("insert into catalog.mileage_plans(id,name,included_km,daily_rate,extra_km_rate,is_active) values(?,?,?,?,?,true)",UUID.randomUUID(),"Standard 200 km",200,0,850);mvc.perform(get("/api/v1/mileage-plans").contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$[0].includedKm").value(200));}
 @Test @WithMockUser(roles="SUPER_ADMIN") void adminCreatesAndUpdatesPlans() throws Exception {String body="{\"name\":\"Unlimited\",\"includedKm\":null,\"dailyRate\":35000,\"extraKmRate\":0}";String id=mvc.perform(post("/api/v1/mileage-plans").contextPath("/api").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*","$1");mvc.perform(put("/api/v1/mileage-plans/{id}",id).contextPath("/api").contentType("application/json").content("{\"name\":\"Unlimited\",\"includedKm\":null,\"dailyRate\":40000,\"extraKmRate\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.dailyRate").value(40000));}
 @Test void anonymousCannotConfigurePlans() throws Exception {mvc.perform(post("/api/v1/mileage-plans").contextPath("/api").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());}
}
