package com.drivique.api.service;

import com.drivique.api.dto.FeatureResponseDTO;
import com.drivique.api.dto.GroupedFeaturesResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.FeatureMapper;
import com.drivique.api.model.Feature;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.FeatureRepository;
import com.drivique.api.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final VehicleRepository vehicleRepository;

    public FeatureService(FeatureRepository featureRepository, VehicleRepository vehicleRepository) {
        this.featureRepository = featureRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public List<FeatureResponseDTO> listAll() {
        return featureRepository.findByActiveTrueOrderByFeatureGroupAscNameAsc().stream()
                .map(FeatureMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupedFeaturesResponseDTO> listGrouped() {
        Map<String, List<FeatureResponseDTO>> grouped = featureRepository.findByActiveTrueOrderByFeatureGroupAscNameAsc()
                .stream()
                .map(FeatureMapper::toDTO)
                .collect(Collectors.groupingBy(FeatureResponseDTO::featureGroup, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> new GroupedFeaturesResponseDTO(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureResponseDTO> listByVehicle(UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        return vehicle.getFeatures().stream()
                .filter(Feature::isActive)
                .sorted(Comparator.comparing(Feature::getName))
                .map(FeatureMapper::toDTO)
                .toList();
    }

    @Transactional
    public List<FeatureResponseDTO> assignFeaturesToVehicle(UUID vehicleId, List<UUID> featureIds) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        List<Feature> toAdd = featureRepository.findAllById(featureIds);
        for (Feature f : toAdd) {
            vehicle.addFeature(f);
        }

        vehicleRepository.saveAndFlush(vehicle);
        return vehicle.getFeatures().stream()
                .map(FeatureMapper::toDTO)
                .toList();
    }

    @Transactional
    public void removeFeatureFromVehicle(UUID vehicleId, UUID featureId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new ResourceNotFoundException("Feature not found: " + featureId));

        vehicle.removeFeature(feature);
        vehicleRepository.saveAndFlush(vehicle);
    }
}
