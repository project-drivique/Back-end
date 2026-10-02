package com.drivique.api.service;

import com.drivique.api.dto.ContractClauseResponseDTO;
import com.drivique.api.model.ContractClause;
import com.drivique.api.repository.ContractClauseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ClauseServiceTests {

    private final ContractClauseRepository repository = mock(ContractClauseRepository.class);
    private ClauseService service;

    @BeforeEach
    void setUp() {
        service = new ClauseService(repository);
    }

    @Test
    void getActiveClauses_WhenRepositoryHasClauses_ReturnsList() {
        ContractClause c1 = new ContractClause("v1.0", (short) 1, "Cláusula 1", "Contenido 1", true);
        ContractClause c2 = new ContractClause("v1.0", (short) 2, "Cláusula 2", "Contenido 2", true);

        when(repository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(c1, c2));

        List<ContractClauseResponseDTO> result = service.getActiveClauses();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Cláusula 1");
        assertThat(result.get(1).sortOrder()).isEqualTo((short) 2);
    }

    @Test
    void getActiveClauses_WhenEmpty_SeedsDefaultClauses() {
        when(repository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<ContractClauseResponseDTO> result = service.getActiveClauses();

        assertThat(result).hasSize(5);
        verify(repository).saveAll(anyList());
    }
}
