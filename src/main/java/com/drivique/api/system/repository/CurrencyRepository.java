package com.drivique.api.system.repository;

import com.drivique.api.system.entity.Currency;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
    List<Currency> findByActiveTrueOrderByCodeAsc();
}
