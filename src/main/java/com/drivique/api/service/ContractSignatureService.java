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
    private final RentalContractRepository contracts; private final UserRepository users; private final CityRepository cities; private final PaymentRepository payments;
    private final ContractStatusRepository statuses; private final FileStorageService storage; private final PdfContractGeneratorService pdfs; private final ContractService contractService;
    public ContractSignatureService(RentalContractRepository contracts, UserRepository users, CityRepository cities, ContractStatusRepository statuses, FileStorageService storage, PdfContractGeneratorService pdfs, ContractService contractService, PaymentRepository payments) { this.contracts=contracts; this.users=users; this.cities=cities; this.statuses=statuses; this.storage=storage; this.pdfs=pdfs; this.contractService=contractService; this.payments=payments; }
    @Transactional
    public ContractResponseDTO sign(UUID id, MultipartFile signature, String strokes, UUID cityId, boolean consentAccepted, String documentVersion, String remoteAddress, String email) {
        if (strokes == null || strokes.isBlank()) throw new IllegalArgumentException("Los trazos biométricos son obligatorios.");
        if (signature != null && (!"image/png".equalsIgnoreCase(signature.getContentType()) || signature.isEmpty())) throw new IllegalArgumentException("La firma debe ser una imagen PNG válida.");
        if (!consentAccepted) throw new IllegalArgumentException("Debe aceptar expresamente el contrato.");
        RentalContract contract=contracts.findById(id).orElseThrow(()->new ResourceNotFoundException("Contrato no encontrado"));
        User user=users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow(()->new ResourceNotFoundException("Usuario no encontrado"));
        if (!contract.getCustomer().getId().equals(user.getId())) throw new ResourceNotFoundException("Contrato no encontrado");
        if (!"PENDING_SIGNATURE".equalsIgnoreCase(contract.getStatus().getCode())) throw new ConflictException("El contrato no está pendiente de firma.");
        if (!contract.getDocumentVersion().equals(documentVersion)) throw new ConflictException("La versión contractual cambió; vuelva a leer el documento.");
        if (!payments.existsByContractIdAndStatusCodeIgnoreCase(id, "APPROVED")) throw new ConflictException("El contrato solo puede firmarse después de un pago aprobado.");
        City city=cities.findById(cityId).orElseThrow(()->new ResourceNotFoundException("Ciudad de firma no encontrada"));
        byte[] signatureBytes=signature == null ? renderStrokes(strokes) : bytes(signature);
        String signatureUrl=signature == null ? storage.storeBytes(signatureBytes, "signature.png", "contracts/signatures") : storage.storeFile(signature, "contracts/signatures");
        byte[] pdf=pdfs.generate(contract, signatureBytes);
        String pdfUrl=storage.storePdf(pdf, "contracts/pdf");
        contract.setSignatureUrl(signatureUrl); contract.setSignatureStrokeData(strokes); contract.setSignedCity(city); contract.setSignedAt(Instant.now());
        contract.setPdfUrl(pdfUrl); contract.setStatus(statuses.findByCodeIgnoreCase("ACTIVE").orElseThrow(()->new ResourceNotFoundException("Estado ACTIVE no encontrado")));
        contract.recordConsent("Acepto el contrato de arrendamiento y sus cláusulas.", sha256(remoteAddress.getBytes(java.nio.charset.StandardCharsets.UTF_8)), sha256(signatureBytes), sha256(pdf));
        return contractService.mapToDTO(contracts.saveAndFlush(contract));
    }
    private byte[] bytes(MultipartFile signature) { try { return signature.getBytes(); } catch (java.io.IOException e) { throw new IllegalStateException("No fue posible leer la firma.", e); } }
    private byte[] renderStrokes(String strokes) {
        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(strokes);
            var image = new java.awt.image.BufferedImage(720, 240, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics(); graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,720,240); graphics.setColor(new java.awt.Color(31,41,55)); graphics.setStroke(new java.awt.BasicStroke(4,java.awt.BasicStroke.CAP_ROUND,java.awt.BasicStroke.JOIN_ROUND));
            boolean drew=false;
            for(var stroke:root){for(int i=1;i<stroke.size();i++){var a=stroke.get(i-1);var b=stroke.get(i);graphics.drawLine(a.path("x").asInt(),a.path("y").asInt(),b.path("x").asInt(),b.path("y").asInt());drew=true;}}
            graphics.dispose(); if(!drew) throw new IllegalArgumentException("La firma no contiene trazos válidos.");
            var output=new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",output); return output.toByteArray();
        } catch(IllegalArgumentException ex){throw ex;} catch(Exception ex){throw new IllegalArgumentException("Los trazos de firma no son válidos.",ex);}
    }
    private String sha256(byte[] value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value)); } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
}
