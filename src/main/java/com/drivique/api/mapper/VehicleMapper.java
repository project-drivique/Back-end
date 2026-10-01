package com.drivique.api.mapper;

import com.drivique.api.dto.VehicleAdminResponseDTO;
import com.drivique.api.dto.VehicleCardResponseDTO;
import com.drivique.api.model.Vehicle;

public final class VehicleMapper {

    private VehicleMapper() {}

    public static VehicleCardResponseDTO toCardDTO(Vehicle v) {
        return new VehicleCardResponseDTO(
                v.getId(),
                v.getPlate(),
                v.getBrand() != null ? v.getBrand().getName() : null,
                v.getModel(),
                v.getCategory() != null ? v.getCategory().getId() : null,
                v.getCategory() != null ? v.getCategory().getName() : null,
                v.getTransmissionType() != null ? v.getTransmissionType().getCode() : null,
                v.getTransmissionType() != null ? v.getTransmissionType().getName() : null,
                v.getFuelType() != null ? v.getFuelType().getCode() : null,
                v.getFuelType() != null ? v.getFuelType().getName() : null,
                v.getCurrentBranch() != null ? v.getCurrentBranch().getId() : null,
                v.getCurrentBranch() != null ? v.getCurrentBranch().getName() : null,
                v.getCurrentBranch() != null && v.getCurrentBranch().getCity() != null ? v.getCurrentBranch().getCity().getName() : null,
                v.getYear(),
                v.getColor(),
                v.getPassengerCapacity(),
                v.getDoorsCount(),
                v.getTrunkCapacityLiters(),
                v.getMileage(),
                v.getDailyRate(),
                v.getMainImageUrl(),
                v.isFeatured(),
                v.isActive(),
                v.getStatus() != null && v.getStatus().allowsReservation()
        );
    }

    public static VehicleAdminResponseDTO toAdminDTO(Vehicle v) {
        return new VehicleAdminResponseDTO(
                v.getId(),
                v.getPlate(),
                v.getVin(),
                v.getBrand() != null ? v.getBrand().getId() : null,
                v.getBrand() != null ? v.getBrand().getName() : null,
                v.getCategory() != null ? v.getCategory().getId() : null,
                v.getCategory() != null ? v.getCategory().getName() : null,
                v.getTransmissionType() != null ? v.getTransmissionType().getId() : null,
                v.getTransmissionType() != null ? v.getTransmissionType().getCode() : null,
                v.getTransmissionType() != null ? v.getTransmissionType().getName() : null,
                v.getFuelType() != null ? v.getFuelType().getId() : null,
                v.getFuelType() != null ? v.getFuelType().getCode() : null,
                v.getFuelType() != null ? v.getFuelType().getName() : null,
                v.getStatus() != null ? v.getStatus().getId() : null,
                v.getStatus() != null ? v.getStatus().getCode() : null,
                v.getStatus() != null ? v.getStatus().getName() : null,
                v.getStatus() != null && v.getStatus().allowsReservation(),
                v.getCurrentBranch() != null ? v.getCurrentBranch().getId() : null,
                v.getCurrentBranch() != null ? v.getCurrentBranch().getName() : null,
                v.getCurrentBranch() != null && v.getCurrentBranch().getCity() != null ? v.getCurrentBranch().getCity().getName() : null,
                v.getModel(),
                v.getYear(),
                v.getColor(),
                v.getPassengerCapacity(),
                v.getDoorsCount(),
                v.getTrunkCapacityLiters(),
                v.getMileage(),
                v.getDailyRate(),
                v.getMainImageUrl(),
                v.isFeatured(),
                v.isActive()
        );
    }
}
