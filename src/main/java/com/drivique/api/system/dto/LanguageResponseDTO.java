package com.drivique.api.system.dto;
import java.util.UUID;
public record LanguageResponseDTO(UUID id, String code, String name, boolean isDefault, boolean isActive) {}
