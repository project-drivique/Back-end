package com.drivique.api.config;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.*;
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "activeLanguages", "latestRates", "activeCities", "departmentCities", "vehicleBrands",
                "transmissionTypes", "fuelTypes", "reservableVehicleStatuses", "vehicleCategories",
                "additionalServices", "insuranceCoverages");
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(256).expireAfterWrite(Duration.ofMinutes(5)));
        manager.setAllowNullValues(false);
        return new TransactionAwareCacheManagerProxy(manager);
    }
}
