package com.drivique.api.repository;

import com.drivique.api.model.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    List<Feature> findByActiveTrueOrderByNameAsc();
    List<Feature> findByActiveTrueOrderByFeatureGroupAscNameAsc();
    List<Feature> findByFeatureGroupIgnoreCaseAndActiveTrueOrderByNameAsc(String featureGroup);
    Optional<Feature> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
