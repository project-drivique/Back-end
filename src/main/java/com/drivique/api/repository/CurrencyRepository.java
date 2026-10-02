package com.drivique.api.repository;

import com.drivique.api.model.Currency;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
    List<Currency> findByActiveTrueOrderByCodeAsc();
    Optional<Currency> findByCodeIgnoreCaseAndActiveTrue(String code);
}
