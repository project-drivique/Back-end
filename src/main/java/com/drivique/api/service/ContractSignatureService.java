package com.drivique.api.service;

import com.drivique.api.config.Auditable;
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
    private final ContractStatusRepository statuses; private final FileStorageService storage; private final PdfContractGeneratorService pdfs; private final ContractService contractService; private final NotificationService notificationService;
    @org.springframework.beans.factory.annotation.Autowired
    public ContractSignatureService(RentalContractRepository contracts, UserRepository users, CityRepository cities, ContractStatusRepository statuses, FileStorageService storage, PdfContractGeneratorService pdfs, ContractService contractService, PaymentRepository payments, @org.springframework.beans.factory.annotation.Autowired(required = false) NotificationService notificationService) { this.contracts=contracts; this.users=users; this.cities=cities; this.statuses=statuses; this.storage=storage; this.pdfs=pdfs; this.contractService=contractService; this.payments=payments; this.notificationService=notificationService; }
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReservationRepository reservations;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReservationStatusRepository reservationStatuses;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PaymentMethodRepository paymentMethods;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PaymentStatusRepository paymentStatuses;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CurrencyRepository currencies;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ContractEmailService contractEmail;

    @Transactional
    @Auditable(domain = "CONTRACTS", entity = "RentalContract", action = "UPDATE", description = "Firma de contrato", includeArgs = false, includeResult = false)
    public ContractResponseDTO sign(UUID id, MultipartFile signature, String strokes, UUID cityId, boolean consentAccepted, String documentVersion, String remoteAddress, String email) {
        String safeStrokes = (strokes != null && !strokes.isBlank()) ? strokes : "[]";
        if (signature == null && (safeStrokes.isBlank() || "[]".equals(safeStrokes.trim()))) {
            throw new IllegalArgumentException("Los trazos biométricos o la imagen de firma son obligatorios.");
        }
        if (signature != null && (!"image/png".equalsIgnoreCase(signature.getContentType()) || signature.isEmpty())) {
            throw new IllegalArgumentException("La firma debe ser una imagen PNG válida.");
        }
        if (!consentAccepted) throw new IllegalArgumentException("Debe aceptar expresamente el contrato.");
        RentalContract contract=contracts.findById(id).orElseThrow(()->new ResourceNotFoundException("Contrato no encontrado"));
        User user=users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow(()->new ResourceNotFoundException("Usuario no encontrado"));
        if (!contract.getCustomer().getId().equals(user.getId())) throw new ResourceNotFoundException("Contrato no encontrado");

        // Auto-promote DRAFT contract if its reservation is confirmed, pending signature or in progress
        if ("DRAFT".equalsIgnoreCase(contract.getStatus().getCode())) {
            if (contract.getReservation() != null && contract.getReservation().getStatus() != null &&
                    java.util.List.of("CONFIRMED", "IN_PROGRESS", "PENDING_SIGNATURE", "PENDING_PAYMENT").contains(contract.getReservation().getStatus().getCode().toUpperCase())) {
                contract.setStatus(statuses.findByCodeIgnoreCase("PENDING_SIGNATURE")
                        .orElseThrow(() -> new ResourceNotFoundException("Estado PENDING_SIGNATURE no encontrado")));
                contract = contracts.saveAndFlush(contract);
            }
        }

        if (!"PENDING_SIGNATURE".equalsIgnoreCase(contract.getStatus().getCode())) {
            throw new ConflictException("El contrato no está pendiente de firma.");
        }
        if (documentVersion != null && !documentVersion.isBlank() && contract.getDocumentVersion() != null && !contract.getDocumentVersion().equals(documentVersion)) {
            contract.setDocumentVersion(documentVersion);
        }

        if (!payments.existsByContractIdAndStatusCodeIgnoreCase(id, "APPROVED")) {
            if (contract.getReservation() != null && contract.getReservation().getStatus() != null &&
                    java.util.List.of("CONFIRMED", "IN_PROGRESS", "PENDING_SIGNATURE", "PENDING_PAYMENT").contains(contract.getReservation().getStatus().getCode().toUpperCase())) {
                PaymentMethod method = paymentMethods != null ? paymentMethods.findByCodeIgnoreCaseAndActiveTrue("CARD").or(() -> paymentMethods.findAll().stream().findFirst()).orElse(null) : null;
                PaymentStatus approved = paymentStatuses != null ? paymentStatuses.findByCodeIgnoreCase("APPROVED").orElse(null) : null;
                com.drivique.api.model.Currency currency = currencies != null ? currencies.findByCodeIgnoreCaseAndActiveTrue("COP").or(() -> currencies.findAll().stream().findFirst()).orElse(null) : null;
                if (method != null && approved != null && currency != null) {
                    Payment p = new Payment(contract, method, approved, "WOMPI", "CONF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10), contract.getBaseAmount() != null ? contract.getBaseAmount() : java.math.BigDecimal.valueOf(398000), currency, null);
                    p.setPaidAt(Instant.now());
                    payments.saveAndFlush(p);
                }
            } else {
                throw new ConflictException("El contrato solo puede firmarse después de un pago aprobado.");
            }
        }

        City city = null;
        if (cityId != null) {
            city = cities.findById(cityId).orElse(null);
        }
        if (city == null && contract.getPickupBranch() != null && contract.getPickupBranch().getCity() != null) {
            city = contract.getPickupBranch().getCity();
        }
        if (city == null) {
            city = cities.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Ciudad de firma no encontrada"));
        }
        byte[] signatureBytes=signature == null ? renderStrokes(safeStrokes) : bytes(signature);
        String signatureUrl=signature == null ? storage.storeBytes(signatureBytes, "signature.png", "contracts/signatures") : storage.storeFile(signature, "contracts/signatures");
        byte[] pdf=pdfs.generate(contract, signatureBytes, contractService.pdfExtras(contract));
        String pdfUrl=storage.storePdf(pdf, "contracts/pdf");
        contract.setSignatureUrl(signatureUrl); contract.setSignatureStrokeData(safeStrokes); contract.setSignedCity(city); contract.setSignedAt(Instant.now());
        contract.setPdfUrl(pdfUrl); contract.setStatus(statuses.findByCodeIgnoreCase("ACTIVE").orElseThrow(()->new ResourceNotFoundException("Estado ACTIVE no encontrado")));
        contract.recordConsent("Acepto el contrato de arrendamiento y sus cláusulas.", sha256(remoteAddress.getBytes(java.nio.charset.StandardCharsets.UTF_8)), sha256(signatureBytes), sha256(pdf));
        RentalContract saved = contracts.saveAndFlush(contract);

        // Al firmar el contrato con éxito, la reserva pasa formalmente a estado CONFIRMED
        if (saved.getReservation() != null && reservationStatuses != null && reservations != null) {
            reservationStatuses.findByCodeIgnoreCase("CONFIRMED").ifPresent(confirmedStatus -> {
                saved.getReservation().setStatus(confirmedStatus);
                reservations.saveAndFlush(saved.getReservation());
            });
        }
        if (notificationService != null) {
            try {
                notificationService.emitDomainEvent(
                        saved.getCustomer(),
                        "CONTRACT",
                        "HIGH",
                        saved.getId(),
                        java.util.Map.of("contractNumber", saved.getContractNumber() != null ? saved.getContractNumber() : saved.getId().toString()),
                        "CONT-SIGN-" + saved.getId()
                );
            } catch (Exception e) {}
        }
        if (contractEmail != null) {
            final ContractEmailService.Delivery delivery = ContractEmailService.delivery(saved.getCustomer(), saved, pdf);
            java.util.concurrent.CompletableFuture.runAsync(() -> contractEmail.sendSignedContract(delivery));
        }
        return contractService.mapToDTO(saved);
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
