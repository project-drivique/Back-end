package com.drivique.api.repository;

import com.drivique.api.model.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractStatusRepository extends JpaRepository<ContractStatus, UUID> {

    Optional<ContractStatus> findByCodeIgnoreCase(String code);
}
