package com.drivique.api.repository;

import com.drivique.api.model.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {
    List<UserDocument> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<UserDocument> findByUserId(UUID userId);
    Optional<UserDocument> findByUserIdAndDocumentType_Id(UUID userId, UUID documentTypeId);
    List<UserDocument> findByStatus_CodeOrderByCreatedAtDesc(String statusCode);
    List<UserDocument> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Query("DELETE FROM UserDocument d WHERE d.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
