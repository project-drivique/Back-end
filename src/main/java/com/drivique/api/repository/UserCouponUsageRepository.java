package com.drivique.api.repository;
import com.drivique.api.model.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserCouponUsageRepository extends JpaRepository<UserCouponUsage,UUID>{ boolean existsByPromotionAndUser(Promotion promotion,User user); }
