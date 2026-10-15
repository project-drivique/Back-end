package com.drivique.api.dto;

public record CashPendingReservationDTO(ReservationResponseDTO reservation, String customerPhone, String customerDocument) {}
