package com.drivique.api.service;

import com.drivique.api.dto.VehicleDocumentRequestDTO;
import com.drivique.api.dto.VehicleDocumentResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.VehicleAssetMapper;
import com.drivique.api.model.Vehicle;
import com.drivique.api.model.VehicleDocument;
import com.drivique.api.repository.VehicleDocumentRepository;
import com.drivique.api.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleDocumentService {

    private final VehicleDocumentRepository documentRepository;
    private final VehicleRepository vehicleRepository;

    public VehicleDocumentService(VehicleDocumentRepository documentRepository, VehicleRepository vehicleRepository) {
        this.documentRepository = documentRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleDocumentResponseDTO> listByVehicle(UUID vehicleId) {
        ensureVehicleExists(vehicleId);
        return documentRepository.findByVehicleIdAndActiveTrueOrderByExpiresAtAsc(vehicleId).stream()
                .map(VehicleAssetMapper::toDTO)
                .toList();
    }

    @Transactional
    public VehicleDocumentResponseDTO addDocument(UUID vehicleId, VehicleDocumentRequestDTO dto) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId));

        if (dto.expiresAt() != null && dto.issuedAt() != null && dto.expiresAt().isBefore(dto.issuedAt())) {
            throw new IllegalArgumentException("Document expiration date cannot be before issuance date");
        }

        // Inactivate any previous active document of the same type for this vehicle
        documentRepository.findByVehicleIdAndDocumentTypeAndActiveTrue(vehicleId, dto.documentType().strip().toUpperCase())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    documentRepository.save(existing);
                });

        VehicleDocument document = new VehicleDocument(
                vehicle,
                dto.documentType().strip().toUpperCase(),
                dto.documentNumber() != null ? dto.documentNumber().strip() : null,
                dto.fileUrl().strip(),
                dto.issuedAt(),
                dto.expiresAt()
        );

        VehicleDocument saved = documentRepository.saveAndFlush(document);
        return VehicleAssetMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<VehicleDocumentResponseDTO> getExpiringDocumentsByVehicle(UUID vehicleId, int daysThreshold) {
        ensureVehicleExists(vehicleId);
        LocalDate threshold = LocalDate.now().plusDays(daysThreshold);
        return documentRepository.findExpiringDocumentsByVehicle(vehicleId, threshold).stream()
                .map(VehicleAssetMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleDocumentResponseDTO> getAllExpiringDocuments(int daysThreshold) {
        LocalDate threshold = LocalDate.now().plusDays(daysThreshold);
        return documentRepository.findExpiringDocuments(threshold).stream()
                .map(VehicleAssetMapper::toDTO)
                .toList();
    }

    @Transactional
    public void deleteDocument(UUID vehicleId, UUID documentId) {
        ensureVehicleExists(vehicleId);
        VehicleDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle document not found: " + documentId));

        if (!document.getVehicle().getId().equals(vehicleId)) {
            throw new IllegalArgumentException("Document does not belong to vehicle: " + vehicleId);
        }

        document.setActive(false);
        documentRepository.saveAndFlush(document);
    }

    private void ensureVehicleExists(UUID vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw new ResourceNotFoundException("Vehicle not found: " + vehicleId);
        }
    }
}
