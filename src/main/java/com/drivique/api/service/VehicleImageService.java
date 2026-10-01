package com.drivique.api.service;

import com.drivique.api.dto.VehicleImageRequestDTO;
import com.drivique.api.dto.VehicleImageResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.VehicleAssetMapper;
import com.drivique.api.model.Vehicle;
import com.drivique.api.model.VehicleImage;
import com.drivique.api.repository.VehicleImageRepository;
import com.drivique.api.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VehicleImageService {

    private final VehicleImageRepository imageRepository;
    private final VehicleRepository vehicleRepository;

    public VehicleImageService(VehicleImageRepository imageRepository, VehicleRepository vehicleRepository) {
        this.imageRepository = imageRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleImageResponseDTO> listByVehicle(UUID vehicleId) {
        ensureVehicleExists(vehicleId);
        return imageRepository.findByVehicleIdOrderBySortOrderAsc(vehicleId).stream()
                .map(VehicleAssetMapper::toDTO)
                .toList();
    }

    @Transactional
    public VehicleImageResponseDTO addImage(UUID vehicleId, VehicleImageRequestDTO dto) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        if (imageRepository.existsByVehicleIdAndSortOrder(vehicleId, dto.sortOrder())) {
            throw new ConflictException("An image with sort order " + dto.sortOrder() + " already exists for this vehicle");
        }

        boolean isPrimary = Boolean.TRUE.equals(dto.isPrimary());
        if (isPrimary) {
            imageRepository.findByVehicleIdAndPrimaryTrue(vehicleId).ifPresent(currentPrimary -> {
                currentPrimary.setPrimary(false);
                imageRepository.save(currentPrimary);
            });
        }

        VehicleImage image = new VehicleImage(vehicle, dto.url().strip(), isPrimary, dto.sortOrder());
        VehicleImage saved = imageRepository.saveAndFlush(image);

        if (isPrimary || vehicle.getMainImageUrl() == null || vehicle.getMainImageUrl().isBlank()) {
            vehicle.setMainImageUrl(saved.getUrl());
            vehicleRepository.save(vehicle);
        }

        return VehicleAssetMapper.toDTO(saved);
    }

    @Transactional
    public VehicleImageResponseDTO setPrimaryImage(UUID vehicleId, UUID imageId) {
        ensureVehicleExists(vehicleId);
        VehicleImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle image not found: " + imageId));

        if (!image.getVehicle().getId().equals(vehicleId)) {
            throw new IllegalArgumentException("Image does not belong to vehicle: " + vehicleId);
        }

        imageRepository.findByVehicleIdAndPrimaryTrue(vehicleId).ifPresent(currentPrimary -> {
            currentPrimary.setPrimary(false);
            imageRepository.save(currentPrimary);
        });

        image.setPrimary(true);
        VehicleImage saved = imageRepository.saveAndFlush(image);

        Vehicle vehicle = image.getVehicle();
        vehicle.setMainImageUrl(saved.getUrl());
        vehicleRepository.save(vehicle);

        return VehicleAssetMapper.toDTO(saved);
    }

    @Transactional
    public void deleteImage(UUID vehicleId, UUID imageId) {
        ensureVehicleExists(vehicleId);
        VehicleImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle image not found: " + imageId));

        if (!image.getVehicle().getId().equals(vehicleId)) {
            throw new IllegalArgumentException("Image does not belong to vehicle: " + vehicleId);
        }

        imageRepository.delete(image);
    }

    private void ensureVehicleExists(UUID vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Vehicle not found: " + vehicleId);
        }
    }
}
