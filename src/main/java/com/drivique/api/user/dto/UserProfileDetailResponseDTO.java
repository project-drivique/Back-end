package com.drivique.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(name = "UserProfileDetailResponse", description = "Detalle completo del perfil del usuario autenticado")
public record UserProfileDetailResponseDTO(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        UUID documentTypeId,
        String documentNumber,
        LocalDate birthDate,
        UUID nationalityId,
        List<String> roles,
        boolean profileComplete,
        String accountStatus,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {}
