package com.drivique.api.controller;

import com.drivique.api.dto.DocumentTypeResponseDTO;
import com.drivique.api.dto.ReviewDocumentRequestDTO;
import com.drivique.api.dto.UserDocumentResponseDTO;
import com.drivique.api.service.UserDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/kyc")
@Tag(name = "KYC - Auditoría y Revisión", description = "Auditoría de identidad, revisión pericial de documentos y catálogos KYC")
public class KycAuditController {

    private final UserDocumentService userDocumentService;

    public KycAuditController(UserDocumentService userDocumentService) {
        this.userDocumentService = userDocumentService;
    }

    @GetMapping("/document-types")
    @Operation(summary = "Listar tipos de documento KYC", description = "Recupera los tipos de documentos soportados (Cédula, Pasaporte, Licencia de Conducción, etc.).")
    @ApiResponse(responseCode = "200", description = "Tipos de documento obtenidos exitosamente")
    public List<DocumentTypeResponseDTO> getDocumentTypes() {
        return userDocumentService.getDocumentTypes();
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'BRANCH_ADMIN', 'SUPER_ADMIN') or hasAuthority('kyc:review')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Listar documentos KYC para auditoría", description = "Permite a los agentes y administradores listar documentos cargados, con filtro opcional por estado (PENDING, APPROVED, REJECTED).")
    @ApiResponse(responseCode = "200", description = "Documentos obtenidos exitosamente")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    public List<UserDocumentResponseDTO> getDocuments(
            @RequestParam(value = "status", required = false) String status,
            Authentication authentication
    ) {
        return userDocumentService.getDocuments(status, authentication.getName());
    }

    @PatchMapping("/documents/{id}/review")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'BRANCH_ADMIN', 'SUPER_ADMIN') or hasAuthority('kyc:review')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Auditar y dictaminar documento KYC",
            description = "Aprueba o rechaza un documento subido por un usuario. Registra auditor y fecha, y si todos los documentos obligatorios están aprobados, marca el perfil como completo (is_profile_complete = true)."
    )
    @ApiResponse(responseCode = "200", description = "Documento auditado exitosamente")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    public UserDocumentResponseDTO reviewDocument(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ReviewDocumentRequestDTO request,
            Authentication authentication
    ) {
        return userDocumentService.reviewDocument(id, authentication.getName(), request);
    }

    @GetMapping("/documents/{id}/file")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'BRANCH_ADMIN', 'SUPER_ADMIN') or hasAuthority('kyc:review')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Visualizar archivo KYC desencriptado en memoria para auditoría",
            description = "Permite a los revisores de sucursal y administradores inspeccionar el documento descifrado. Registra traza de auditoría."
    )
    @ApiResponse(responseCode = "200", description = "Stream del documento descifrado")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    public ResponseEntity<byte[]> getDocumentFile(
            @PathVariable("id") UUID id,
            @RequestParam(value = "side", defaultValue = "front") String side,
            Authentication authentication,
            HttpServletRequest request
    ) {
        var docFile = userDocumentService.getDocumentFile(id, side, authentication.getName(), request.getRemoteAddr());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(docFile.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + docFile.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(docFile.content());
    }

    @GetMapping("/documents/file")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'BRANCH_ADMIN', 'SUPER_ADMIN') or hasAuthority('kyc:review')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Visualizar archivo KYC por ruta interna protegida",
            description = "Resuelve la ruta interna cifrada del documento, descifra los bytes en memoria y audita la consulta."
    )
    @ApiResponse(responseCode = "200", description = "Stream del documento descifrado")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    public ResponseEntity<byte[]> getDocumentFileByPath(
            @RequestParam("path") String path,
            Authentication authentication,
            HttpServletRequest request
    ) {
        var docFile = userDocumentService.getDocumentFileByPath(path, authentication.getName(), request.getRemoteAddr());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(docFile.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + docFile.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(docFile.content());
    }
}
