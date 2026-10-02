package com.drivique.api.service;

import com.drivique.api.dto.DeliveryPointRequestDTO;
import com.drivique.api.dto.DeliveryPointResponseDTO;
import org.springframework.security.access.AccessDeniedException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.CityRepository;
import com.drivique.api.repository.ReservationDeliveryPointRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReservationDeliveryPointService {

    private final ReservationDeliveryPointRepository deliveryPointRepository;
    private final ReservationRepository reservationRepository;
    private final BranchRepository branchRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;

    public ReservationDeliveryPointService(
            ReservationDeliveryPointRepository deliveryPointRepository,
            ReservationRepository reservationRepository,
            BranchRepository branchRepository,
            CityRepository cityRepository,
            UserRepository userRepository
    ) {
        this.deliveryPointRepository = deliveryPointRepository;
        this.reservationRepository = reservationRepository;
        this.branchRepository = branchRepository;
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<DeliveryPointResponseDTO> configureDeliveryPoints(
            UUID reservationId,
            List<DeliveryPointRequestDTO> requestPoints,
            String userEmail
    ) {
        Reservation reservation = findAndAuthorizeReservation(reservationId, userEmail);

        if (requestPoints == null || requestPoints.isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar al menos un punto de entrega/devolución");
        }

        List<DeliveryPointResponseDTO> responseList = new ArrayList<>();
        for (DeliveryPointRequestDTO dto : requestPoints) {
            DeliveryPointResponseDTO savedDto = saveOrUpdateDeliveryPoint(reservation, dto);
            responseList.add(savedDto);
        }

        return responseList;
    }

    @Transactional
    public DeliveryPointResponseDTO setSingleDeliveryPoint(
            UUID reservationId,
            DeliveryPointRequestDTO dto,
            String userEmail
    ) {
        Reservation reservation = findAndAuthorizeReservation(reservationId, userEmail);
        return saveOrUpdateDeliveryPoint(reservation, dto);
    }

    @Transactional(readOnly = true)
    public List<DeliveryPointResponseDTO> getDeliveryPointsByReservation(UUID reservationId, String userEmail) {
        Reservation reservation = findAndAuthorizeReservation(reservationId, userEmail);
        return deliveryPointRepository.findByReservation(reservation)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional
    public DeliveryPointResponseDTO saveOrUpdateDeliveryPoint(Reservation reservation, DeliveryPointRequestDTO dto) {
        if (dto.pointType() == null || dto.pointType().isBlank()) {
            throw new IllegalArgumentException("El tipo de punto (pointType) es obligatorio");
        }
        String cleanPointType = dto.pointType().strip().toUpperCase();
        if (!cleanPointType.equals("PICKUP") && !cleanPointType.equals("RETURN")) {
            throw new IllegalArgumentException("El tipo de punto debe ser PICKUP o RETURN");
        }

        if (dto.modality() == null || dto.modality().isBlank()) {
            throw new IllegalArgumentException("La modalidad (modality) es obligatoria");
        }
        String cleanModality = dto.modality().strip().toUpperCase();
        if (!List.of("BRANCH", "HOME_DELIVERY", "AIRPORT", "TERMINAL").contains(cleanModality)) {
            throw new IllegalArgumentException("Modalidad inválida. Debe ser BRANCH, HOME_DELIVERY, AIRPORT o TERMINAL");
        }

        Branch branch = null;
        City city = null;
        String neighborhood = dto.neighborhood() != null && !dto.neighborhood().isBlank() ? dto.neighborhood().strip() : null;
        String address = dto.address() != null && !dto.address().isBlank() ? dto.address().strip() : null;
        String flightOrBus = dto.flightOrBusNumber() != null && !dto.flightOrBusNumber().isBlank() ? dto.flightOrBusNumber().strip() : null;
        String reference = dto.referenceDetails() != null && !dto.referenceDetails().isBlank() ? dto.referenceDetails().strip() : null;

        switch (cleanModality) {
            case "BRANCH" -> {
                if (dto.branchId() == null) {
                    throw new IllegalArgumentException("El branchId es obligatorio para la modalidad BRANCH");
                }
                branch = branchRepository.findById(dto.branchId())
                        .filter(Branch::isActive)
                        .orElseThrow(() -> new ResourceNotFoundException("Sede activa no encontrada con ID: " + dto.branchId()));
                city = null;
                address = null;
            }
            case "HOME_DELIVERY" -> {
                if (dto.cityId() == null) {
                    throw new IllegalArgumentException("El cityId es obligatorio para la modalidad HOME_DELIVERY");
                }
                city = cityRepository.findById(dto.cityId())
                        .filter(City::isActive)
                        .orElseThrow(() -> new ResourceNotFoundException("Ciudad activa no encontrada con ID: " + dto.cityId()));
                if (address == null || address.isBlank()) {
                    throw new IllegalArgumentException("La dirección es obligatoria para la modalidad HOME_DELIVERY");
                }
                branch = null;
            }
            case "AIRPORT", "TERMINAL" -> {
                if (dto.cityId() == null) {
                    throw new IllegalArgumentException("El cityId es obligatorio para la modalidad " + cleanModality);
                }
                city = cityRepository.findById(dto.cityId())
                        .filter(City::isActive)
                        .orElseThrow(() -> new ResourceNotFoundException("Ciudad activa no encontrada con ID: " + dto.cityId()));
                branch = null;
            }
        }

        ReservationDeliveryPoint point = deliveryPointRepository
                .findByReservationAndPointType(reservation, cleanPointType)
                .orElseGet(() -> new ReservationDeliveryPoint(
                        reservation,
                        cleanPointType,
                        cleanModality,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        point.setPointType(cleanPointType);
        point.setModality(cleanModality);
        point.setBranch(branch);
        point.setCity(city);
        point.setNeighborhood(neighborhood);
        point.setAddress(address);
        point.setFlightOrBusNumber(flightOrBus);
        point.setReferenceDetails(reference);
        point.setUpdatedAt(Instant.now());

        ReservationDeliveryPoint savedPoint = deliveryPointRepository.save(point);
        return mapToDTO(savedPoint);
    }

    private Reservation findAndAuthorizeReservation(UUID reservationId, String userEmail) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservationId));

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario autenticado no encontrado"));

        boolean isOwner = Objects.equals(reservation.getCustomer().getId(), user.getId());
        boolean isStaff = user.getRoles().stream()
                .anyMatch(r -> List.of("ADMIN", "SUPER_ADMIN", "AGENT").contains(r.getCode()));

        if (!isOwner && !isStaff) {
            throw new AccessDeniedException("No tiene permisos para gestionar los puntos de esta reserva");
        }

        return reservation;
    }

    public DeliveryPointResponseDTO mapToDTO(ReservationDeliveryPoint point) {
        return new DeliveryPointResponseDTO(
                point.getId(),
                point.getReservation().getId(),
                point.getPointType(),
                point.getModality(),
                point.getBranch() != null ? point.getBranch().getId() : null,
                point.getBranch() != null ? point.getBranch().getName() : null,
                point.getCity() != null ? point.getCity().getId() : null,
                point.getCity() != null ? point.getCity().getName() : null,
                point.getNeighborhood(),
                point.getAddress(),
                point.getFlightOrBusNumber(),
                point.getReferenceDetails(),
                point.getCreatedAt(),
                point.getUpdatedAt()
        );
    }
}
