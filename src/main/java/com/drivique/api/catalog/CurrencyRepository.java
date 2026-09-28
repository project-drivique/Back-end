package com.drivique.api.catalog;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
    List<Currency> findByActiveTrueOrderByCodeAsc();
}
