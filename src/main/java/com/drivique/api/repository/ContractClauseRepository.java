package com.drivique.api.repository;

import com.drivique.api.model.ContractClause;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractClauseRepository extends JpaRepository<ContractClause, UUID> {

    List<ContractClause> findByIsActiveTrueOrderBySortOrderAsc();

    List<ContractClause> findByVersionAndIsActiveTrueOrderBySortOrderAsc(String version);

    List<ContractClause> findAllByOrderByVersionAscSortOrderAsc();
}
