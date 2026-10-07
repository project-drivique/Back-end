package com.drivique.api.repository;
import com.drivique.api.model.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponUsageRepository extends JpaRepository<UserCouponUsage, UUID> {
    boolean existsByPromotionAndUser(Promotion promotion, User user);

    @Modifying
    @Query("DELETE FROM UserCouponUsage c WHERE c.user = :user")
    void deleteByUser(@Param("user") User user);
}
