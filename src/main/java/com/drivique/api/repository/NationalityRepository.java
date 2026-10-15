package com.drivique.api.repository;

import com.drivique.api.model.Nationality;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NationalityRepository extends JpaRepository<Nationality, UUID> {
    List<Nationality> findByActiveTrueOrderByNameAsc();
}
