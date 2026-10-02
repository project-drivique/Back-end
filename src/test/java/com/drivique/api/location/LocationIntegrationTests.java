package com.drivique.api.location;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.model.City;
import com.drivique.api.model.Department;
import com.drivique.api.repository.CityRepository;
import com.drivique.api.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class LocationIntegrationTests extends DatabaseHealthTestSupport {
    @Autowired MockMvc mvc;
    @Autowired DepartmentRepository departments;
    @Autowired CityRepository cities;
    @Autowired CacheManager caches;

    @BeforeEach
    void resetLocations() {
        resetFleetTables();
        resetLocationTables();
        caches.getCache("activeCities").clear();
        caches.getCache("departmentCities").clear();
    }

    @Test
    void publicEndpointsReturnOnlyActiveDepartmentsAndCitiesWithTransportFlags() throws Exception {
        Department antioquia = departments.saveAndFlush(new Department("Antioquia"));
        Department inactive = departments.saveAndFlush(new Department("Retired"));
        inactive.deactivate();
        departments.saveAndFlush(inactive);
        City medellin = cities.saveAndFlush(new City(antioquia, "Medellín", true, true));
        City hidden = cities.saveAndFlush(new City(antioquia, "Hidden", false, false));
        hidden.deactivate();
        cities.saveAndFlush(hidden);

        mvc.perform(get("/api/v1/departments").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Antioquia"));
        mvc.perform(get("/api/v1/cities").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(medellin.getId().toString()))
                .andExpect(jsonPath("$[0].hasAirport").value(true))
                .andExpect(jsonPath("$[0].hasTerminal").value(true));
        mvc.perform(get("/api/v1/departments/{id}/cities", antioquia.getId()).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Medellín"));
        mvc.perform(get("/api/v1/departments/{id}/cities", inactive.getId()).contextPath("/api"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousAndCustomerCannotCreateOrUpdateCities() throws Exception {
        Department department = departments.saveAndFlush(new Department("Cundinamarca"));
        String payload = payload(department.getId(), "Bogotá", true, true);
        mvc.perform(post("/api/v1/cities").contextPath("/api").contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/cities/{id}", UUID.randomUUID()).contextPath("/api")
                .contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotManageCities() throws Exception {
        Department department = departments.saveAndFlush(new Department("Cundinamarca"));
        mvc.perform(post("/api/v1/cities").contextPath("/api").contentType("application/json")
                .content(payload(department.getId(), "Bogotá", true, true))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BRANCH_ADMIN")
    void branchAdminCreatesUpdatesAndInvalidatesCityCaches() throws Exception {
        Department department = departments.saveAndFlush(new Department("Cundinamarca"));
        Department boyaca = departments.saveAndFlush(new Department("Boyacá"));
        City city = cities.saveAndFlush(new City(department, "Bogotá", true, true));
        mvc.perform(get("/api/v1/cities").contextPath("/api"))
                .andExpect(jsonPath("$[0].name").value("Bogotá"));
        jdbc.update("update location.cities set name = 'Bogotá cache' where id = ?", city.getId());
        mvc.perform(get("/api/v1/cities").contextPath("/api"))
                .andExpect(jsonPath("$[0].name").value("Bogotá"));

        mvc.perform(put("/api/v1/cities/{id}", city.getId()).contextPath("/api")
                .contentType("application/json").content(payload(boyaca.getId(), "Tunja", false, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Boyacá"))
                .andExpect(jsonPath("$.name").value("Tunja"))
                .andExpect(jsonPath("$.hasAirport").value(false))
                .andExpect(jsonPath("$.hasTerminal").value(true));
        mvc.perform(get("/api/v1/cities").contextPath("/api"))
                .andExpect(jsonPath("$[0].name").value("Tunja"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminValidatesDepartmentsAndDuplicateCities() throws Exception {
        Department department = departments.saveAndFlush(new Department("Valle"));
        String payload = payload(department.getId(), "Cali", true, true);
        mvc.perform(post("/api/v1/cities").contextPath("/api").contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isActive").value(true));
        mvc.perform(post("/api/v1/cities").contextPath("/api").contentType("application/json").content(payload))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/cities").contextPath("/api").contentType("application/json")
                .content(payload(UUID.randomUUID(), "Palmira", false, true))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/cities").contextPath("/api").contentType("application/json")
                .content("{\"departmentId\":\"" + department.getId() + "\",\"name\":\" \",\"hasAirport\":false,\"hasTerminal\":false}"))
                .andExpect(status().isBadRequest());
    }

    private String payload(UUID departmentId, String name, boolean hasAirport, boolean hasTerminal) {
        return "{\"departmentId\":\"" + departmentId + "\",\"name\":\"" + name
                + "\",\"hasAirport\":" + hasAirport + ",\"hasTerminal\":" + hasTerminal + "}";
    }
}
