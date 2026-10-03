package com.drivique.api.controller;

import com.drivique.api.dto.MessageResponseDTO;
import com.drivique.api.dto.NotificationResponseDTO;
import com.drivique.api.dto.SendNotificationRequestDTO;
import com.drivique.api.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
@Tag(name = "Notifications", description = "Centro de notificaciones multicanal para usuarios")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Consultar notificaciones del usuario", description = "Obtiene las notificaciones del usuario autenticado con filtro opcional de lectura.")
    @ApiResponse(responseCode = "200", description = "Listado de notificaciones obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public List<NotificationResponseDTO> getNotifications(
            @RequestParam(value = "isRead", required = false) Boolean isRead,
            @RequestParam(value = "read", required = false) Boolean read,
            Authentication authentication
    ) {
        Boolean filter = isRead != null ? isRead : read;
        return notificationService.getNotifications(authentication.getName(), filter);
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marcar notificación como leída", description = "Actualiza el estado de una notificación a leída para el usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Notificación marcada como leída")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Notificación no encontrada")
    public NotificationResponseDTO markAsRead(@PathVariable UUID id, Authentication authentication) {
        return notificationService.markAsRead(id, authentication.getName());
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Marcar todas las notificaciones como leídas", description = "Marca todas las notificaciones no leídas del usuario autenticado como leídas.")
    @ApiResponse(responseCode = "200", description = "Todas las notificaciones fueron marcadas como leídas")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public MessageResponseDTO markAllAsRead(Authentication authentication) {
        int count = notificationService.markAllAsRead(authentication.getName());
        return MessageResponseDTO.of("Se marcaron " + count + " notificaciones como leídas.");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EMPLOYEE', 'BRANCH_ADMIN')")
    @Operation(summary = "Enviar notificación a usuario", description = "Permite enviar una notificación multicanal a un usuario (Admin/Employee).")
    @ApiResponse(responseCode = "201", description = "Notificación enviada y registrada exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public NotificationResponseDTO sendNotification(@Valid @RequestBody SendNotificationRequestDTO request) {
        return notificationService.sendNotification(request);
    }
}
