package com.drivique.api.model;

import java.io.Serializable;
import java.util.UUID;

public record BranchUserId(UUID userId, UUID branchId) implements Serializable {
}
