package com.drivique.api.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate; import java.util.UUID;
public record PromotionValidationRequestDTO(@NotBlank @Size(max=50) String code,@NotNull UUID vehicleId,@NotNull UUID categoryId,@NotNull LocalDate pickupDate,@NotNull LocalDate returnDate) {}
