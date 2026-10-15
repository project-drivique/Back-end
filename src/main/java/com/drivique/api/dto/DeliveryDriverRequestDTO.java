package com.drivique.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record DeliveryDriverRequestDTO(
        UUID branchId,
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String fullName,
        @Email(message = "El formato de correo no es válido") @Size(max = 254) String email,
        @Size(max = 30) String phone,
        @NotBlank @Pattern(regexp = "CC|CE|PAS|NIT", message = "Tipo de documento inválido") String documentType,
        @NotBlank(message = "El número de documento es obligatorio") @Size(max = 40) String documentNumber,
        @Size(max = 40) String licenseNumber,
        @Size(max = 5) String licenseCategory,
        LocalDate licenseExpiresOn,
        @Size(max = 120) String vehicleDescription,
        @Pattern(regexp = "AVAILABLE|ON_ROUTE|INACTIVE", message = "Estado inválido") String status
) {}
