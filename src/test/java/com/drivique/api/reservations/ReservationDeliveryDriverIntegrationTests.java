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
class ReservationDeliveryDriverIntegrationTests extends ReservationDeliveryTestBase {

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void staffManagesDriversAndAssignsThemToHomeDelivery() throws Exception {
        configureHomeReturn();
        createStaff("staff@drivique.com", branchId);

        String created = mvc.perform(post("/api/v1/admin/delivery-drivers").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(DRIVER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.branchName").value("Sede El Poblado"))
                .andReturn().getResponse().getContentAsString();
        String driverId = objectMapper.readTree(created).get("id").asText();

        mvc.perform(post("/api/v1/admin/delivery-drivers").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content(DRIVER))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.driverName").value("Carlos Ramírez"))
                .andExpect(jsonPath("$.deliveryPin").doesNotExist());

        // La entrega por sucursal no admite conductor
        mvc.perform(post("/api/v1/admin/deliveries/{id}/PICKUP/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isConflict());

        String pin = jdbc.queryForObject("SELECT delivery_pin FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class);

        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/complete", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\": \"" + ("0000".equals(pin) ? "1111" : "0000") + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/complete", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\": \"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT status FROM location.delivery_drivers", String.class)).isEqualTo("AVAILABLE");

        mvc.perform(delete("/api/v1/admin/delivery-drivers/{id}", driverId).contextPath("/api"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void onlyTheCustomerSeesTheDeliveryPin() throws Exception {
        configureHomeReturn();
        createStaff("staff@drivique.com", branchId);
        jdbc.execute("INSERT INTO location.delivery_drivers (id, branch_id, full_name, document_type, document_number, status, created_at, updated_at) "
                + "SELECT gen_random_uuid(), '" + branchId + "', 'Conductor', 'CC', '123', 'AVAILABLE', now(), now()");
        jdbc.execute("UPDATE rental.reservation_delivery_points SET delivery_pin = '4821', delivery_status = 'ASSIGNED', "
                + "driver_id = (SELECT id FROM location.delivery_drivers) WHERE point_type = 'RETURN'");

        mvc.perform(get("/api/v1/reservations/{id}/delivery-points", reservationId).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pointType=='RETURN')].deliveryPin").value("4821"));
    }

    @Test
    @WithMockUser(username = "otherstaff@drivique.com", roles = "BRANCH_ADMIN")
    void staffFromAnotherBranchCannotUseThisReservation() throws Exception {
        configureHomeReturn();
        Branch otherBranch = branchRepository.saveAndFlush(new Branch("Sede Laureles", "Cra 70 # 1-1",
                cityRepository.findById(cityId).orElseThrow(), "3009999999", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        createStaff("otherstaff@drivique.com", otherBranch.getId());
        jdbc.execute("INSERT INTO location.delivery_drivers (id, branch_id, full_name, document_type, document_number, status, created_at, updated_at) "
                + "SELECT gen_random_uuid(), '" + otherBranch.getId() + "', 'Conductor', 'CC', '999', 'AVAILABLE', now(), now()");
        String driverId = jdbc.queryForObject("SELECT id::text FROM location.delivery_drivers", String.class);

        mvc.perform(post("/api/v1/admin/deliveries/{id}/RETURN/assign", reservationId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\": \"" + driverId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void staffListsRepliesAndDeletesBranchReviews() throws Exception {
        createStaff("staff@drivique.com", branchId);
        UUID reviewId = createReview();

        mvc.perform(get("/api/v1/admin/reviews/branches").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Carlos Gomez"))
                .andExpect(jsonPath("$[0].reservationCode").value("RES-2026-9999"))
                .andExpect(jsonPath("$[0].managerReply").doesNotExist());

        mvc.perform(patch("/api/v1/admin/reviews/branches/{id}/reply", reviewId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\": \"Gracias por tu opinión\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerReply").value("Gracias por tu opinión"));

        mvc.perform(patch("/api/v1/admin/reviews/branches/{id}/reply", reviewId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\": \"  \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/admin/reviews/branches/{id}", reviewId).contextPath("/api"))
                .andExpect(status().isNoContent());
        assertThat(branchReviewRepository.count()).isZero();
    }

    @Test
    @WithMockUser(username = "otherstaff@drivique.com", roles = "BRANCH_ADMIN")
    void staffFromAnotherBranchCannotSeeOrAnswerThisReview() throws Exception {
        UUID reviewId = createReview();
        Branch otherBranch = branchRepository.saveAndFlush(new Branch("Sede Laureles", "Cra 70 # 1-1",
                cityRepository.findById(cityId).orElseThrow(), "3009999999", LocalTime.of(8, 0), LocalTime.of(18, 0), true));
        createStaff("otherstaff@drivique.com", otherBranch.getId());

        mvc.perform(get("/api/v1/admin/reviews/branches").contextPath("/api"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(patch("/api/v1/admin/reviews/branches/{id}/reply", reviewId).contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reply\": \"Hola\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void driverConfirmsFromTheEmailLinkAndEveryoneIsNotified() throws Exception {
        String token = assignDriverAndGetToken("CONFIRM");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject = 'Tu conductor ya fue asignado' AND message LIKE '%PIN%'", Integer.class)).isEqualTo(2);

        // abrir el enlace solo muestra la página: no confirma nada por sí solo
        mvc.perform(get("/api/v1/public/deliveries/action").contextPath("/api").param("token", token))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class)).isEqualTo("ASSIGNED");

        mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM location.delivery_drivers", String.class)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject LIKE '%confirmada%'", Integer.class)).isGreaterThan(0);

        // un segundo clic (o un reintento) es idempotente: informa que ya está completada y no repite ningún efecto
        int notificationsBefore = jdbc.queryForObject("SELECT count(*) FROM support.notifications", Integer.class);
        mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Entrega ya completada")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications", Integer.class)).isEqualTo(notificationsBefore);
    }

    @Test
    @WithMockUser(username = "staff@drivique.com", roles = "BRANCH_ADMIN")
    void driverRejectsTheCodeAndTheDeliveryStaysPending() throws Exception {
        String token = assignDriverAndGetToken("REJECT");
        mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT delivery_status FROM rental.reservation_delivery_points WHERE point_type = 'RETURN'", String.class)).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject = 'Código de devolución rechazado'", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.notifications WHERE subject = 'Revisa tu código de devolución'", Integer.class)).isGreaterThan(0);
    }

    @Test
    void tamperedOrExpiredLinksAreRefused() throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        String expired = actionTokens.create(id, "RETURN", "CONFIRM", java.time.Instant.now().minusSeconds(10));
        String tampered = actionTokens.create(id, "RETURN", "CONFIRM", java.time.Instant.now().plusSeconds(600)) + "x";
        for (String token : java.util.List.of(expired, tampered, "basura")) {
            mvc.perform(post("/api/v1/public/deliveries/action").contextPath("/api").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token))
                    .andExpect(status().isGone());
        }
    }
}
