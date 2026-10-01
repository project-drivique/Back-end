package com.drivique.api.dto;
import jakarta.validation.constraints.*;
public record VehicleBrandRequestDTO(@NotBlank @Size(max=100) String name){}
