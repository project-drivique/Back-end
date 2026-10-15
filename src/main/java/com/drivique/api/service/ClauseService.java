package com.drivique.api.service;

import com.drivique.api.dto.ContractClauseResponseDTO;
import com.drivique.api.model.ContractClause;
import com.drivique.api.repository.ContractClauseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClauseService {

    private final ContractClauseRepository contractClauseRepository;

    public ClauseService(ContractClauseRepository contractClauseRepository) {
        this.contractClauseRepository = contractClauseRepository;
    }

    @Transactional(readOnly = true)
    public List<ContractClauseResponseDTO> getActiveClauses() {
        List<ContractClause> clauses = activeClauseEntities();
        return clauses.stream()
                .map(this::mapToDTO)
                .toList();
    }

    /** Cláusulas vigentes: salen de la base de datos (se cargan con las migraciones), nunca del código. */
    @Transactional(readOnly = true)
    public List<ContractClause> activeClauseEntities() {
        return contractClauseRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    public ContractClauseResponseDTO mapToDTO(ContractClause clause) {
        return new ContractClauseResponseDTO(
                clause.getId(),
                clause.getVersion(),
                clause.getSortOrder(),
                clause.getTitle(),
                clause.getContent(),
                clause.isActive()
        );
    }
}
