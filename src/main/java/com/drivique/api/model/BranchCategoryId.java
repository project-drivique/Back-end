package com.drivique.api.model;

import java.io.Serializable;
import java.util.UUID;

public record BranchCategoryId(UUID branchId, UUID categoryId) implements Serializable {
}
