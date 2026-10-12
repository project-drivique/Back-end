package com.drivique.api.controller;

import com.drivique.api.dto.ContractClauseResponseDTO;
import com.drivique.api.dto.ContractResponseDTO;
import com.drivique.api.dto.GenerateContractRequestDTO;
import com.drivique.api.service.ClauseService;
import com.drivique.api.service.ContractService;
import com.drivique.api.service.ContractSignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/contracts")
@Tag(name = "Contracts", description = "Endpoints para la emisión y consulta de contratos legales de arrendamiento vehicular y cláusulas")
@SecurityRequirement(name = "bearerAuth")
public class ContractController {

    private final ContractService contractService;
    private final ClauseService clauseService;
    private final ContractSignatureService signatureService;

    public ContractController(ContractService contractService, ClauseService clauseService, ContractSignatureService signatureService) {
        this.contractService = contractService;
        this.clauseService = clauseService;
        this.signatureService = signatureService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Emitir y generar contrato legal de arrendamiento",
            description = "Genera el contrato legal oficial con consecutivo único CTR-YYYY-XXXX a partir de una reserva confirmada, asociando automáticamente las cláusulas vigentes."
    )
    @ApiResponse(responseCode = "201", description = "Contrato generado exitosamente")
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    @ApiResponse(responseCode = "409", description = "Contrato previamente generado para la reserva o reserva cancelada")
    public ContractResponseDTO generateContract(
            @Valid @RequestBody GenerateContractRequestDTO request,
            Authentication authentication
    ) {
        return contractService.generateContract(request, authentication.getName());
    }

    @PostMapping("/generate/reservation-code/{code}")
    public ContractResponseDTO getOrGenerateByReservationCode(@PathVariable String code, Authentication authentication) {
        return contractService.getOrGenerateByReservationCode(code, authentication.getName());
    }

    @PostMapping(path = "/{id}/sign", consumes = "multipart/form-data")
    @Operation(summary = "Firmar contrato y generar PDF oficial")
    public ContractResponseDTO sign(
            @PathVariable UUID id,
            @RequestPart(value = "signature", required = false) MultipartFile signature,
            @RequestPart("signatureStrokeData") String signatureStrokeData,
            @RequestPart("signedCityId") String signedCityId,
            @RequestPart("consentAccepted") Boolean consentAccepted,
            @RequestPart("documentVersion") String documentVersion,
            HttpServletRequest servletRequest,
            Authentication authentication
    ) {
        return signatureService.sign(id, signature, signatureStrokeData, UUID.fromString(signedCityId),
                Boolean.TRUE.equals(consentAccepted), documentVersion, servletRequest.getRemoteAddr(), authentication.getName());
    }

    @GetMapping("/clauses")
    @Operation(
            summary = "Consultar cláusulas contractuales vigentes",
            description = "Obtiene la lista de todas las cláusulas legales vigentes ordenadas por orden de aparición."
    )
    @ApiResponse(responseCode = "200", description = "Listado de cláusulas vigentes")
    public List<ContractClauseResponseDTO> getActiveClauses() {
        return clauseService.getActiveClauses();
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener contrato por ID",
            description = "Recupera la información completa del contrato de arrendamiento, incluyendo cláusulas, partes, tarifas y estado de firma."
    )
    @ApiResponse(responseCode = "200", description = "Contrato encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Contrato no encontrado")
    public ContractResponseDTO getContractById(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return contractService.getContractById(id, authentication.getName());
    }

    @GetMapping("/reservation/{reservationId}")
    @Operation(
            summary = "Obtener contrato por ID de reserva",
            description = "Recupera el contrato legal asociado a una reserva vehicular específica."
    )
    @ApiResponse(responseCode = "200", description = "Contrato encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Contrato no encontrado para la reserva")
    public ContractResponseDTO getContractByReservationId(
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return contractService.getContractByReservationId(reservationId, authentication.getName());
    }

    @GetMapping("/number/{contractNumber}")
    @Operation(
            summary = "Obtener contrato por número consecutivo",
            description = "Recupera el contrato legal mediante su número único de carátula (ej. CTR-2026-0001)."
    )
    @ApiResponse(responseCode = "200", description = "Contrato encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Contrato no encontrado")
    public ContractResponseDTO getContractByNumber(
            @PathVariable String contractNumber,
            Authentication authentication
    ) {
        return contractService.getContractByNumber(contractNumber, authentication.getName());
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id, Authentication authentication) {
        byte[] pdf = contractService.downloadPdf(id, authentication.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract-" + id + ".pdf")
                .body(pdf);
    }
}
