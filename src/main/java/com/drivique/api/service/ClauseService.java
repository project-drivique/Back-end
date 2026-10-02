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
        List<ContractClause> clauses = getOrSeedDefaultClauses();
        return clauses.stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional
    public List<ContractClause> getOrSeedDefaultClauses() {
        List<ContractClause> activeClauses = contractClauseRepository.findByIsActiveTrueOrderBySortOrderAsc();
        if (!activeClauses.isEmpty()) {
            return activeClauses;
        }

        List<ContractClause> defaultClauses = List.of(
                new ContractClause("v1.0", (short) 1, "Objeto del Contrato", "El arrendador entrega en arrendamiento al arrendatario el vehículo automotor descrito en la carátula del presente contrato.", true),
                new ContractClause("v1.0", (short) 2, "Uso y Destinación", "El arrendatario se compromete a usar el vehículo exclusivamente para transporte particular lícito dentro del territorio nacional autorizado.", true),
                new ContractClause("v1.0", (short) 3, "Obligaciones del Arrendatario", "El arrendatario deberá restituir el vehículo en la fecha, hora y sede acordadas, en el mismo estado mecánico y estético recibido.", true),
                new ContractClause("v1.0", (short) 4, "Depósito de Garantía y Cargos", "El arrendatario autoriza el bloqueo del depósito de garantía y el cobro de deducibles, multas de tránsito o combustible faltante.", true),
                new ContractClause("v1.0", (short) 5, "Firma y Validez Jurídica", "Las partes reconocen plena validez legal y probatoria a la firma electrónica y biométrica capturada en la plataforma Drivique.", true)
        );

        return contractClauseRepository.saveAll(defaultClauses);
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
