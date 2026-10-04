package com.drivique.api.notifications;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Notification;
import com.drivique.api.model.User;
import com.drivique.api.repository.NotificationRepository;
import com.drivique.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class NotificationIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
        resetIamTables();

        testUser = userRepository.saveAndFlush(new User("Carlos", "Gomez", "customer@drivique.com", "hashedpassword"));
        otherUser = userRepository.saveAndFlush(new User("Maria", "Lopez", "other@drivique.com", "hashedpassword"));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void getNotifications_ReturnsOnlyAuthenticatedUserNotifications() throws Exception {
        notificationRepository.saveAndFlush(new Notification(
                testUser, "IN_APP", "RESERVATION", "Reserva confirmada", "Tu reserva #101 está lista", UUID.randomUUID()
        ));
        notificationRepository.saveAndFlush(new Notification(
                testUser, "EMAIL", "SECURITY", "Cambio de clave", "Clave modificada", null
        ));
        notificationRepository.saveAndFlush(new Notification(
                otherUser, "IN_APP", "GENERAL", "Notificación ajena", "No debe verse", null
        ));

        mvc.perform(get("/api/v1/notifications").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].subject").value("Cambio de clave"))
                .andExpect(jsonPath("$[1].subject").value("Reserva confirmada"));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void getNotifications_WithIsReadFilter_ReturnsFilteredList() throws Exception {
        notificationRepository.saveAndFlush(new Notification(
                testUser, "IN_APP", "RESERVATION", "Reserva pendiente", "Por favor completa el pago", null
        ));
        Notification readNotif = new Notification(
                testUser, "IN_APP", "GENERAL", "Bienvenido", "Gracias por registrarte", null
        );
        readNotif.markAsRead();
        notificationRepository.saveAndFlush(readNotif);

        // Filter unread
        mvc.perform(get("/api/v1/notifications")
                        .contextPath("/api")
                        .param("isRead", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("Reserva pendiente"))
                .andExpect(jsonPath("$[0].isRead").value(false));

        // Filter read
        mvc.perform(get("/api/v1/notifications")
                        .contextPath("/api")
                        .param("isRead", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("Bienvenido"))
                .andExpect(jsonPath("$[0].isRead").value(true));
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void markAsRead_UpdatesSingleNotificationSuccessfully() throws Exception {
        Notification notification = notificationRepository.saveAndFlush(new Notification(
                testUser, "IN_APP", "RESERVATION", "Reserva confirmada", "Tu reserva está lista", null
        ));

        assertThat(notification.isRead()).isFalse();

        mvc.perform(patch("/api/v1/notifications/{id}/read", notification.getId()).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notification.getId().toString()))
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
        assertThat(updated.getReadAt()).isNotNull();
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void markAsRead_WhenTryingToReadOtherUserNotification_Returns404() throws Exception {
        Notification otherNotification = notificationRepository.saveAndFlush(new Notification(
                otherUser, "IN_APP", "SECURITY", "Alerta privada", "Mensaje confidencial", null
        ));

        mvc.perform(patch("/api/v1/notifications/{id}/read", otherNotification.getId()).contextPath("/api"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "customer@drivique.com")
    void markAllAsRead_UpdatesAllUnreadNotifications() throws Exception {
        notificationRepository.saveAndFlush(new Notification(
                testUser, "IN_APP", "RESERVATION", "Notif 1", "Mensaje 1", null
        ));
        notificationRepository.saveAndFlush(new Notification(
                testUser, "EMAIL", "PROMOTION", "Notif 2", "Mensaje 2", null
        ));
        notificationRepository.saveAndFlush(new Notification(
                testUser, "SMS", "SECURITY", "Notif 3", "Mensaje 3", null
        ));

        assertThat(notificationRepository.countByUserIdAndIsReadFalse(testUser.getId())).isEqualTo(3);

        mvc.perform(patch("/api/v1/notifications/read-all").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se marcaron 3 notificaciones como leídas."));

        assertThat(notificationRepository.countByUserIdAndIsReadFalse(testUser.getId())).isEqualTo(0);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sendNotification_AsAdmin_CreatesAndReturnsNotification() throws Exception {
        String payload = """
                {
                    "userId": "%s",
                    "channel": "IN_APP",
                    "type": "RESERVATION",
                    "subject": "Reserva aprobada",
                    "message": "Tu reserva ha sido aprobada por el administrador.",
                    "referenceId": "%s"
                }
                """.formatted(testUser.getId(), UUID.randomUUID());

        mvc.perform(post("/api/v1/notifications")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.channel").value("IN_APP"))
                .andExpect(jsonPath("$.type").value("RESERVATION"))
                .andExpect(jsonPath("$.subject").value("Reserva aprobada"))
                .andExpect(jsonPath("$.isRead").value(false));

        assertThat(notificationRepository.countByUserIdAndIsReadFalse(testUser.getId())).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void sendNotification_AsCustomer_Returns403Forbidden() throws Exception {
        String payload = """
                {
                    "userId": "%s",
                    "channel": "IN_APP",
                    "type": "GENERAL",
                    "subject": "Test",
                    "message": "Mensaje"
                }
                """.formatted(testUser.getId());

        mvc.perform(post("/api/v1/notifications")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAccess_Returns401Unauthorized() throws Exception {
        mvc.perform(get("/api/v1/notifications").contextPath("/api"))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/notifications/%s/read".formatted(UUID.randomUUID())).contextPath("/api"))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/notifications/read-all").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }
}
