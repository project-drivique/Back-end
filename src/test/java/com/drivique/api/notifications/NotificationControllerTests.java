package com.drivique.api.notifications;

import com.drivique.api.controller.NotificationController;
import com.drivique.api.dto.NotificationResponseDTO;
import com.drivique.api.dto.SendNotificationRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTests {

    private MockMvc mvc;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Authentication authentication;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getNotifications_ReturnsList() throws Exception {
        when(authentication.getName()).thenReturn("user@drivique.com");

        UUID notifId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationResponseDTO dto = new NotificationResponseDTO(
                notifId,
                userId,
                "IN_APP",
                "RESERVATION",
                "Reserva confirmada",
                "Tu reserva está lista",
                null,
                false,
                Instant.now(),
                null,
                Instant.now()
        );

        when(notificationService.getNotifications("user@drivique.com", null)).thenReturn(List.of(dto));

        mvc.perform(get("/v1/notifications").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("Reserva confirmada"))
                .andExpect(jsonPath("$[0].channel").value("IN_APP"));
    }

    @Test
    void getNotifications_WithIsReadParam_CallsServiceWithFilter() throws Exception {
        when(authentication.getName()).thenReturn("user@drivique.com");
        when(notificationService.getNotifications("user@drivique.com", false)).thenReturn(List.of());

        mvc.perform(get("/v1/notifications")
                        .param("isRead", "false")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(notificationService).getNotifications("user@drivique.com", false);
    }

    @Test
    void markAsRead_ReturnsUpdatedNotification() throws Exception {
        UUID notifId = UUID.randomUUID();
        when(authentication.getName()).thenReturn("user@drivique.com");

        NotificationResponseDTO dto = new NotificationResponseDTO(
                notifId,
                UUID.randomUUID(),
                "EMAIL",
                "SECURITY",
                "Seguridad",
                "Mensaje",
                null,
                true,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(notificationService.markAsRead(notifId, "user@drivique.com")).thenReturn(dto);

        mvc.perform(patch("/v1/notifications/{id}/read", notifId).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notifId.toString()))
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void markAllAsRead_ReturnsMessage() throws Exception {
        when(authentication.getName()).thenReturn("user@drivique.com");
        when(notificationService.markAllAsRead("user@drivique.com")).thenReturn(3);

        mvc.perform(patch("/v1/notifications/read-all").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Se marcaron 3 notificaciones como leídas."));
    }

    @Test
    void sendNotification_ValidPayload_ReturnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        NotificationResponseDTO dto = new NotificationResponseDTO(
                notifId,
                userId,
                "IN_APP",
                "GENERAL",
                "Aviso importante",
                "Contenido del aviso",
                null,
                false,
                Instant.now(),
                null,
                Instant.now()
        );

        when(notificationService.sendNotification(any(SendNotificationRequestDTO.class))).thenReturn(dto);

        String payload = """
                {
                    "userId": "%s",
                    "channel": "IN_APP",
                    "type": "GENERAL",
                    "subject": "Aviso importante",
                    "message": "Contenido del aviso"
                }
                """.formatted(userId);

        mvc.perform(post("/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notifId.toString()))
                .andExpect(jsonPath("$.subject").value("Aviso importante"));
    }
}
