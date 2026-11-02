package com.drivique.api.reservations;

import com.drivique.api.DatabaseHealthTestSupport;

import com.drivique.api.dto.ConfigureDeliveryPointsRequestDTO;
import com.drivique.api.dto.DeliveryPointRequestDTO;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ReservationDeliveryPointIntegrationTests extends ReservationDeliveryTestBase {

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void configureDeliveryPoints_BranchAndHomeDelivery_SuccessfullyPersistedAndRetrieved() throws Exception {
        DeliveryPointRequestDTO pickup = new DeliveryPointRequestDTO(
                "PICKUP",
                "BRANCH",
                branchId,
                null,
                null,
                null,
                null,
                "Recoger en sala de espera"
        );

        DeliveryPointRequestDTO returnPoint = new DeliveryPointRequestDTO(
                "RETURN",
                "HOME_DELIVERY",
                null,
                cityId,
                "El Poblado",
                "Calle 10 # 43E-20",
                null,
                "Entregar en la portería del edificio"
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(pickup, returnPoint));

        // 1. Configure delivery points (POST)
        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].pointType").value("PICKUP"))
                .andExpect(jsonPath("$[0].modality").value("BRANCH"))
                .andExpect(jsonPath("$[0].branchId").value(branchId.toString()))
                .andExpect(jsonPath("$[0].branchName").value("Sede El Poblado"))
                .andExpect(jsonPath("$[1].pointType").value("RETURN"))
                .andExpect(jsonPath("$[1].modality").value("HOME_DELIVERY"))
                .andExpect(jsonPath("$[1].cityName").value("Medellín"))
                .andExpect(jsonPath("$[1].neighborhood").value("El Poblado"))
                .andExpect(jsonPath("$[1].address").value("Calle 10 # 43E-20"));

        // 2. Query delivery points (GET)
        mvc.perform(get("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void updateSingleDeliveryPoint_AirportAndTerminal_Success() throws Exception {
        DeliveryPointRequestDTO airportDto = new DeliveryPointRequestDTO(
                "PICKUP",
                "AIRPORT",
                null,
                cityId,
                null,
                null,
                "AV9301",
                "Puerta 4 llegadas nacionales"
        );

        mvc.perform(put("/api/v1/reservations/{id}/delivery-points/{pointType}", reservationId, "PICKUP")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(airportDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointType").value("PICKUP"))
                .andExpect(jsonPath("$.modality").value("AIRPORT"))
                .andExpect(jsonPath("$.flightOrBusNumber").value("AV9301"))
                .andExpect(jsonPath("$.referenceDetails").value("Puerta 4 llegadas nacionales"));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void configureDeliveryPoints_HomeDeliveryMissingAddress_Returns400() throws Exception {
        DeliveryPointRequestDTO invalidHome = new DeliveryPointRequestDTO(
                "RETURN",
                "HOME_DELIVERY",
                null,
                cityId,
                "Laureles",
                "",
                null,
                null
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(invalidHome));

        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "other@drivique.com")
    void configureDeliveryPoints_OtherUser_Returns403() throws Exception {
        DeliveryPointRequestDTO pickup = new DeliveryPointRequestDTO(
                "PICKUP",
                "BRANCH",
                branchId,
                null,
                null,
                null,
                null,
                null
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(pickup));

        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "ADMIN")
    void configureDeliveryPoints_AdminStaff_AllowsConfiguration() throws Exception {
        DeliveryPointRequestDTO terminalDto = new DeliveryPointRequestDTO(
                "PICKUP",
                "TERMINAL",
                null,
                cityId,
                null,
                null,
                "BOLIV-200",
                "Bahía 5"
        );

        ConfigureDeliveryPointsRequestDTO body = new ConfigureDeliveryPointsRequestDTO(List.of(terminalDto));

        mvc.perform(post("/api/v1/reservations/{id}/delivery-points", reservationId)
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].modality").value("TERMINAL"))
                .andExpect(jsonPath("$[0].flightOrBusNumber").value("BOLIV-200"));
    }
}
