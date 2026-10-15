package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Ficha de la sucursal del encargado: datos propios, horario, categorías, encargado y flota. */
public record BranchProfileDTO(
        UUID id,
        String name,
        String cityName,
        String address,
        String phone,
        String pickupInstructions,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean active,
        boolean allowsCashPayment,
        String managerName,
        long vehicleCount,
        int vehicleCapacity,
        List<String> categories
) {}
