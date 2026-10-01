package com.drivique.api.mapper;
import com.drivique.api.dto.BranchResponseDTO;
import com.drivique.api.model.Branch;
public final class BranchMapper { private BranchMapper() {} public static BranchResponseDTO branch(Branch value) {
    return new BranchResponseDTO(value.getId(), value.getName(), value.getAddress(), value.getCity().getId(), value.getCity().getName(), value.getPhone(), value.getOpeningTime(), value.getClosingTime(), value.allowsCashPayment(), value.isActive());
} }
