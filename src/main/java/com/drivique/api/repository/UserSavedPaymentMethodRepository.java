package com.drivique.api.repository;
import com.drivique.api.model.User;
import com.drivique.api.model.UserSavedPaymentMethod;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSavedPaymentMethodRepository extends JpaRepository<UserSavedPaymentMethod, UUID> {
    boolean existsByPaymentToken(String token);

    @Modifying
    @Query("DELETE FROM UserSavedPaymentMethod p WHERE p.user = :user")
    void deleteByUser(@Param("user") User user);
}
