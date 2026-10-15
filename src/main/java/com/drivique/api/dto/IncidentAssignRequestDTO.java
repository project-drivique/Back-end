package com.drivique.api.dto;

import java.util.UUID;

/** Si assigneeUserId es nulo, la incidencia se asigna a quien hace la solicitud. */
public record IncidentAssignRequestDTO(UUID assigneeUserId) {
}
