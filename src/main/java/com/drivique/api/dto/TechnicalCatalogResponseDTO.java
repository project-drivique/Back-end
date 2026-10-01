package com.drivique.api.dto;
import java.util.UUID;
public record TechnicalCatalogResponseDTO(UUID id,String code,String name,boolean isActive,boolean allowsReservation){}
