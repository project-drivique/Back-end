package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "UserProfileResponse", description = "Resumen del perfil del usuario autenticado")
public record UserProfileResponseDTO(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        List<String> roles,
        boolean profileComplete,
        String accountStatus
) {}
