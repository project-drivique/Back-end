package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "UserProfileResponse", description = "Resumen del perfil del usuario autenticado, roles, permisos y sede asignada")
public record UserProfileResponseDTO(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        List<String> roles,
        boolean profileComplete,
        String accountStatus,
        UUID branchId,
        String branchName,
        List<String> permissions
) {
    public UserProfileResponseDTO(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String phone,
            List<String> roles,
            boolean profileComplete,
            String accountStatus
    ) {
        this(id, firstName, lastName, email, phone, roles, profileComplete, accountStatus, null, null, List.of());
    }
}
