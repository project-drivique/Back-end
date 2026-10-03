package com.drivique.api.dto;import java.util.*;public record VehicleReviewsResponseDTO(UUID vehicleId,double averageRating,long reviewCount,List<VehicleReviewResponseDTO> reviews){}
