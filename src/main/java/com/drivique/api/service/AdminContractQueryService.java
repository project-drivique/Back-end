package com.drivique.api.service;

import com.drivique.api.dto.ContractResponseDTO;
import com.drivique.api.model.RentalContract;
import com.drivique.api.repository.RentalContractRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminContractQueryService {

    private final RentalContractRepository contracts;
    private final ContractService contractService;
    private final StaffBranchScope scope;

    public AdminContractQueryService(RentalContractRepository contracts, ContractService contractService, StaffBranchScope scope) {
        this.contracts = contracts;
        this.contractService = contractService;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<ContractResponseDTO> list(String staffEmail) {
        Optional<UUID> branchId = scope.forStaff(staffEmail);
        return contracts.findAll().stream()
                .filter(c -> branchId.isEmpty() || belongs(c, branchId.get()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(contractService::mapToDTO)
                .toList();
    }

    private boolean belongs(RentalContract c, UUID branchId) {
        return c.getPickupBranch() != null && branchId.equals(c.getPickupBranch().getId());
    }
}
