package com.drivique.api.repository;

import com.drivique.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Query("select distinct u from User u join u.roles r where r.code = :roleCode and u.deletedAt is null order by u.firstName")
    java.util.List<User> findActiveByRoleCode(@org.springframework.data.repository.query.Param("roleCode") String roleCode);
    @org.springframework.data.jpa.repository.Query("select d.code from DocumentType d where d.id = :id")
    Optional<String> findDocumentTypeCodeById(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDocumentNumber(String documentNumber);
    boolean existsByDocumentNumberAndIdNot(String documentNumber, UUID id);
}
