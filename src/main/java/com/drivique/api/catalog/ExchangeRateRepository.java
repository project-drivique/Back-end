package com.drivique.api.catalog;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    @Query("""
            select r from ExchangeRate r
            join fetch r.fromCurrency f join fetch r.toCurrency t
            where f.active = true and t.active = true
              and not exists (select newer.id from ExchangeRate newer
                where newer.fromCurrency = f and newer.toCurrency = t and newer.fetchedAt > r.fetchedAt)
            order by f.code, t.code
            """)
    List<ExchangeRate> findLatestForActivePairs();
}
