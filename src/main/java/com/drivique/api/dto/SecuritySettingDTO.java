package com.drivique.api.dto;

import java.time.Instant;

/** Parámetro global editable: valor actual, reglas de validación y si el último cambio se puede revertir. */
public record SecuritySettingDTO(
        String key,
        String value,
        String description,
        String type,
        Integer min,
        Integer max,
        Instant updatedAt,
        boolean canRevert,
        String previousValue,
        String lastChangedBy) {}
