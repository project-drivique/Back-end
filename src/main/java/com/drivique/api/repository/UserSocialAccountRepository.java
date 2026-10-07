package com.drivique.api.repository;

import com.drivique.api.model.UserSocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, UUID> {

    Optional<UserSocialAccount> findByProviderIgnoreCaseAndProviderUserId(String provider, String providerUserId);

    Optional<UserSocialAccount> findByUserIdAndProviderIgnoreCase(UUID userId, String provider);

    List<UserSocialAccount> findAllByUserId(UUID userId);

    boolean existsByProviderIgnoreCaseAndProviderUserId(String provider, String providerUserId);

    long countByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM UserSocialAccount s WHERE s.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
