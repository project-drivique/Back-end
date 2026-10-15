package com.drivique.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Vehículo al que aplica (o con el que se ilustra) una promoción. */
public record PromotionVehicleDTO(UUID id, String name, String categoryName, BigDecimal dailyRate, List<String> imageUrls) {}
