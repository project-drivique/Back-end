package com.drivique.api.service;

import com.drivique.api.dto.VehicleCardResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.VehicleMapper;
import com.drivique.api.model.User;
import com.drivique.api.model.UserFavoriteVehicle;
import com.drivique.api.model.UserFavoriteVehicleId;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.UserFavoriteVehicleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserFavoriteService {

    private final UserFavoriteVehicleRepository favoriteRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    public UserFavoriteService(
            UserFavoriteVehicleRepository favoriteRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository
    ) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public void addFavorite(String userEmail, UUID vehicleId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        if (!vehicle.isActive()) {
            throw new ResourceNotFoundException("Vehicle not found: " + vehicleId);
        }

        if (!favoriteRepository.existsByUserIdAndVehicleId(user.getId(), vehicle.getId())) {
            favoriteRepository.save(new UserFavoriteVehicle(user, vehicle));
        }
    }

    @Transactional
    public void removeFavorite(String userEmail, UUID vehicleId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        favoriteRepository.deleteById(new UserFavoriteVehicleId(user.getId(), vehicleId));
    }

    @Transactional(readOnly = true)
    public List<VehicleCardResponseDTO> listFavorites(String userEmail) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        return favoriteRepository.findByUserIdWithVehicle(user.getId()).stream()
                .map(fav -> VehicleMapper.toCardDTO(fav.getVehicle()))
                .toList();
    }
}
