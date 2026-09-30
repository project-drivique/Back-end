package com.drivique.api.user.controller;

import com.drivique.api.user.dto.UpdateUserPreferenceRequestDTO;
import com.drivique.api.user.dto.UpdateUserProfileRequestDTO;
import com.drivique.api.user.dto.UserProfileDetailResponseDTO;
import com.drivique.api.user.dto.UserPreferenceResponseDTO;
import com.drivique.api.user.service.UserPreferenceService;
import com.drivique.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "Users & Preferences", description = "Gestión de perfil del usuario autenticado y configuración de preferencias")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final UserPreferenceService preferenceService;

    public UserController(UserService userService, UserPreferenceService preferenceService) {
        this.userService = userService;
        this.preferenceService = preferenceService;
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener perfil propio", description = "Recupera la información detallada del perfil del usuario autenticado vía JWT.")
    @ApiResponse(responseCode = "200", description = "Perfil obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    public UserProfileDetailResponseDTO getMyProfile(Authentication authentication) {
        return userService.getProfile(authentication.getName());
    }

    @PutMapping("/me")
    @Operation(summary = "Actualizar perfil propio", description = "Actualiza campos permitidos del perfil (nombre, apellido, teléfono, fecha de nacimiento, nacionalidad). El email y documento están protegidos.")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado exitosamente")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    public UserProfileDetailResponseDTO updateMyProfile(
            @Valid @RequestBody UpdateUserProfileRequestDTO request,
            Authentication authentication
    ) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @GetMapping("/me/preferences")
    @Operation(summary = "Obtener preferencias", description = "Recupera el idioma, moneda, tema y canales de notificación configurados para el usuario.")
    @ApiResponse(responseCode = "200", description = "Preferencias obtenidas exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    public UserPreferenceResponseDTO getMyPreferences(Authentication authentication) {
        return preferenceService.getPreferences(authentication.getName());
    }

    @PutMapping("/me/preferences")
    @Operation(summary = "Actualizar preferencias", description = "Actualiza las preferencias de visualización (LIGHT/DARK/SYSTEM), idioma, moneda y flags de notificaciones (email/SMS).")
    @ApiResponse(responseCode = "200", description = "Preferencias actualizadas exitosamente")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    public UserPreferenceResponseDTO updateMyPreferences(
            @Valid @RequestBody UpdateUserPreferenceRequestDTO request,
            Authentication authentication
    ) {
        return preferenceService.updatePreferences(authentication.getName(), request);
    }
}
