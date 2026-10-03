package com.drivique.api.dto;import java.time.Instant;import java.util.UUID;public record VehicleReviewResponseDTO(UUID id,String customerName,short rating,String comment,Instant createdAt){}
