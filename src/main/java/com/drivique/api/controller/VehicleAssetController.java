package com.drivique.api.controller;

import com.drivique.api.dto.VehicleDocumentRequestDTO;
import com.drivique.api.dto.VehicleDocumentResponseDTO;
import com.drivique.api.dto.VehicleImageRequestDTO;
import com.drivique.api.dto.VehicleImageResponseDTO;
import com.drivique.api.service.VehicleDocumentService;
import com.drivique.api.service.VehicleImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/vehicles")
@Tag(name = "Fleet Assets", description = "Vehicle gallery images and legal/technical document management")
@PreAuthorize("hasAuthority('fleet:manage') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class VehicleAssetController {

    private final VehicleImageService imageService;
    private final VehicleDocumentService documentService;

    public VehicleAssetController(VehicleImageService imageService, VehicleDocumentService documentService) {
        this.imageService = imageService;
        this.documentService = documentService;
    }

    // --- IMAGES ENDPOINTS ---

    @GetMapping("/{id}/images")
    @Operation(summary = "Listar la galería de imágenes de un vehículo")
    public List<VehicleImageResponseDTO> listImages(@PathVariable UUID id) {
        return imageService.listByVehicle(id);
    }

    @PostMapping("/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar una imagen a la galería del vehículo")
    public VehicleImageResponseDTO addImage(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleImageRequestDTO input
    ) {
        return imageService.addImage(id, input);
    }

    @PatchMapping("/{id}/images/{imageId}/primary")
    @Operation(summary = "Marcar una imagen como la foto principal del vehículo")
    public VehicleImageResponseDTO setPrimaryImage(
            @PathVariable UUID id,
            @PathVariable UUID imageId
    ) {
        return imageService.setPrimaryImage(id, imageId);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una imagen de la galería del vehículo")
    public void deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        imageService.deleteImage(id, imageId);
    }

    // --- DOCUMENTS ENDPOINTS ---

    @GetMapping("/documents/expiring")
    @Operation(summary = "Alertas globales de documentos de la flota próximos a vencer")
    public List<VehicleDocumentResponseDTO> listAllExpiringDocuments(
            @RequestParam(defaultValue = "30") int days
    ) {
        return documentService.getAllExpiringDocuments(days);
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "Listar los documentos técnicos y legales activos de un vehículo")
    public List<VehicleDocumentResponseDTO> listDocuments(@PathVariable UUID id) {
        return documentService.listByVehicle(id);
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un documento técnico/legal (SOAT, Tecnomecánica) para el vehículo")
    public VehicleDocumentResponseDTO addDocument(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleDocumentRequestDTO input
    ) {
        return documentService.addDocument(id, input);
    }

    @GetMapping("/{id}/documents/expiring")
    @Operation(summary = "Consultar documentos del vehículo próximos a vencer o vencidos")
    public List<VehicleDocumentResponseDTO> listVehicleExpiringDocuments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "30") int days
    ) {
        return documentService.getExpiringDocumentsByVehicle(id, days);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Inactivar o eliminar lógicamente un documento del vehículo")
    public void deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        documentService.deleteDocument(id, documentId);
    }
}
