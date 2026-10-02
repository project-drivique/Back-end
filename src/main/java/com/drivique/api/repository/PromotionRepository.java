package com.drivique.api.repository;
import com.drivique.api.model.Promotion;
import java.time.Instant; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PromotionRepository extends JpaRepository<Promotion,UUID>{ Optional<Promotion> findByCodeIgnoreCase(String code); List<Promotion> findByActiveTrueAndOfferTypeAndStartsAtLessThanEqualAndEndsAtAfterOrderByEndsAtAsc(String offerType,Instant now,Instant sameNow); }
