package com.drivique.api.location;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev") @AutoConfigureMockMvc
class BranchIntegrationTests extends DatabaseHealthTestSupport {
 @Autowired MockMvc mvc; @Autowired BranchRepository branches; @Autowired CityRepository cities; @Autowired DepartmentRepository departments;
 @BeforeEach void resetLocations() { resetFleetTables(); resetLocationTables(); }
 @Test void publicEndpointsOnlyExposeActiveBranches() throws Exception {
  City city = city(); Branch active = branches.saveAndFlush(new Branch("Centro", "Calle 1", city, "123", java.time.LocalTime.of(8,0), java.time.LocalTime.of(18,0), true));
  Branch inactive = branches.saveAndFlush(new Branch("Norte", "Calle 2", city, "456", java.time.LocalTime.of(8,0), java.time.LocalTime.of(18,0), false)); inactive.toggleStatus(); branches.saveAndFlush(inactive);
  mvc.perform(get("/api/v1/branches").contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/v1/branches/{id}", active.getId()).contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$.cityName").value("Bogotá"));
  mvc.perform(get("/api/v1/cities/{id}/branches", city.getId()).contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
 }
 @Test @WithMockUser(roles="BRANCH_ADMIN") void adminCreatesTogglesAndValidatesHours() throws Exception {
  City city = city(); String valid = payload(city.getId(), "Centro", "08:00", "18:00");
  mvc.perform(post("/api/v1/branches").contextPath("/api").contentType("application/json").content(valid)).andExpect(status().isCreated()).andExpect(jsonPath("$.allowsCashPayment").value(true));
  UUID id = branches.findAll().getFirst().getId();
  mvc.perform(patch("/api/v1/branches/{id}/toggle-status", id).contextPath("/api")).andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
  mvc.perform(put("/api/v1/branches/{id}", id).contextPath("/api").contentType("application/json").content(payload(city.getId(), "Centro", "18:00", "18:00"))).andExpect(status().isBadRequest());
 }
 @Test void anonymousCannotManage() throws Exception { City city=city(); mvc.perform(post("/api/v1/branches").contextPath("/api").contentType("application/json").content(payload(city.getId(),"Centro","08:00","18:00"))).andExpect(status().isUnauthorized()); }
 private City city() { Department d=departments.saveAndFlush(new Department("Cundinamarca")); return cities.saveAndFlush(new City(d,"Bogotá",true,true)); }
 private String payload(UUID city, String name, String open, String close) { return "{\"name\":\""+name+"\",\"address\":\"Calle 1\",\"cityId\":\""+city+"\",\"phone\":\"123\",\"openingTime\":\""+open+"\",\"closingTime\":\""+close+"\",\"allowsCashPayment\":true}"; }
}
