package com.drivique.api.system.repository;

import com.drivique.api.system.entity.Language;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface LanguageRepository extends JpaRepository<Language, UUID> {
    List<Language> findByActiveTrueOrderByCodeAsc();
    boolean existsByCodeOrName(String code, String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Language l where l.id = :id")
    Optional<Language> findForUpdate(@Param("id") UUID id);
}
