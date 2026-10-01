package com.drivique.api.dto;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.UUID;
public record BranchRequestDTO(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 255) String address,
        @NotNull UUID cityId, @NotBlank @Size(max = 30) String phone, @NotNull LocalTime openingTime,
        @NotNull LocalTime closingTime, boolean allowsCashPayment) {}
