package com.drivique.api.service;

import com.drivique.api.dto.ContractResponseDTO;
import com.drivique.api.exception.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContractSignatureService {
    private final RentalContractRepository contracts; private final UserRepository users; private final CityRepository cities;
    private final ContractStatusRepository statuses; private final FileStorageService storage; private final PdfContractGeneratorService pdfs; private final ContractService contractService;
    public ContractSignatureService(RentalContractRepository contracts, UserRepository users, CityRepository cities, ContractStatusRepository statuses, FileStorageService storage, PdfContractGeneratorService pdfs, ContractService contractService) { this.contracts=contracts; this.users=users; this.cities=cities; this.statuses=statuses; this.storage=storage; this.pdfs=pdfs; this.contractService=contractService; }
    @Transactional
    public ContractResponseDTO sign(UUID id, MultipartFile signature, String strokes, UUID cityId, String email) {
        if (signature == null || !"image/png".equalsIgnoreCase(signature.getContentType()) || signature.isEmpty()) throw new IllegalArgumentException("La firma debe ser una imagen PNG válida.");
        if (strokes == null || strokes.isBlank()) throw new IllegalArgumentException("Los trazos biométricos son obligatorios.");
        RentalContract contract=contracts.findById(id).orElseThrow(()->new ResourceNotFoundException("Contrato no encontrado"));
        User user=users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow(()->new ResourceNotFoundException("Usuario no encontrado"));
        if (!contract.getCustomer().getId().equals(user.getId())) throw new ResourceNotFoundException("Contrato no encontrado");
        if (!"PENDING_SIGNATURE".equalsIgnoreCase(contract.getStatus().getCode())) throw new ConflictException("El contrato no está pendiente de firma.");
        City city=cities.findById(cityId).orElseThrow(()->new ResourceNotFoundException("Ciudad de firma no encontrada"));
        String signatureUrl=storage.storeFile(signature, "contracts/signatures");
        byte[] pdf=pdfs.generate(contract, bytes(signature));
        String pdfUrl=storage.storePdf(pdf, "contracts/pdf");
        contract.setSignatureUrl(signatureUrl); contract.setSignatureStrokeData(strokes); contract.setSignedCity(city); contract.setSignedAt(Instant.now());
        contract.setPdfUrl(pdfUrl); contract.setStatus(statuses.findByCodeIgnoreCase("ACTIVE").orElseThrow(()->new ResourceNotFoundException("Estado ACTIVE no encontrado")));
        return contractService.mapToDTO(contracts.saveAndFlush(contract));
    }
    private byte[] bytes(MultipartFile signature) { try { return signature.getBytes(); } catch (java.io.IOException e) { throw new IllegalStateException("No fue posible leer la firma.", e); } }
}
