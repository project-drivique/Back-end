package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Datos para registrar o actualizar un punto de entrega o devolución de una reserva")
public record DeliveryPointRequestDTO(
        @NotBlank(message = "El tipo de punto es obligatorio")
        @Pattern(regexp = "^(?i)(PICKUP|RETURN)$", message = "El tipo de punto debe ser PICKUP o RETURN")
        @Schema(description = "Tipo de punto: PICKUP (recogida) o RETURN (devolución)", example = "PICKUP")
        String pointType,

        @NotBlank(message = "La modalidad es obligatoria")
        @Pattern(regexp = "^(?i)(BRANCH|HOME_DELIVERY|AIRPORT|TERMINAL)$", message = "La modalidad debe ser BRANCH, HOME_DELIVERY, AIRPORT o TERMINAL")
        @Schema(description = "Modalidad de entrega: BRANCH, HOME_DELIVERY, AIRPORT, TERMINAL", example = "AIRPORT")
        String modality,

        @Schema(description = "ID de la sede (obligatorio solo si modality=BRANCH)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID branchId,

        @Schema(description = "ID de la ciudad (obligatorio si modality es HOME_DELIVERY, AIRPORT o TERMINAL)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID cityId,

        @Size(max = 120, message = "El barrio no puede exceder 120 caracteres")
        @Schema(description = "Barrio o sector (requerido para HOME_DELIVERY)", example = "El Poblado")
        String neighborhood,

        @Size(max = 255, message = "La dirección no puede exceder 255 caracteres")
        @Schema(description = "Dirección exacta (obligatorio para HOME_DELIVERY)", example = "Calle 10 # 43E-20")
        String address,

        @Size(max = 60, message = "El número de vuelo o bus no puede exceder 60 caracteres")
        @Schema(description = "Número de vuelo o bus (validado para AIRPORT o TERMINAL)", example = "AV9301")
        String flightOrBusNumber,

        @Size(max = 500, message = "Los detalles de referencia no pueden exceder 500 caracteres")
        @Schema(description = "Detalles de referencia u observaciones adicionales", example = "Puerta 4 llegadas nacionales")
        String referenceDetails
) {}
