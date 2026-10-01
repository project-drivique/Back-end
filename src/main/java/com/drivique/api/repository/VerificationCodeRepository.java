package com.drivique.api.repository;

import com.drivique.api.model.User;
import com.drivique.api.model.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    @Query("SELECT vc FROM VerificationCode vc WHERE vc.user = :user AND vc.purpose = :purpose AND vc.usedAt IS NULL ORDER BY vc.createdAt DESC LIMIT 1")
    Optional<VerificationCode> findLatestActiveCode(@Param("user") User user, @Param("purpose") String purpose);

    Optional<VerificationCode> findByUserAndPurposeAndCodeHash(User user, String purpose, String codeHash);
}
