package com.drivique.api.controller;

import com.drivique.api.dto.UserDocumentResponseDTO;
import com.drivique.api.service.UserDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users/me/documents")
@Tag(name = "KYC - User Documents", description = "Subida y consulta de documentos de identidad y licencias de conducción del usuario")
@SecurityRequirement(name = "bearerAuth")
public class UserDocumentController {

    private final UserDocumentService userDocumentService;

    public UserDocumentController(UserDocumentService userDocumentService) {
        this.userDocumentService = userDocumentService;
    }

    @GetMapping
    @Operation(
            summary = "Consultar documentos KYC del usuario autenticado",
            description = "Recupera la lista de documentos de identidad cargados por el usuario y su estado de verificación actual (PENDING, APPROVED, REJECTED)."
    )
    @ApiResponse(responseCode = "200", description = "Lista de documentos obtenida exitosamente")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    public List<UserDocumentResponseDTO> getMyDocuments(Authentication authentication) {
        return userDocumentService.getUserDocuments(authentication.getName());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Subir documento KYC (Cédula o Licencia)",
            description = "Permite al usuario subir archivos de identificación (frontal y opcional trasero). Formatos soportados: JPG, PNG, PDF (máx. 5MB)."
    )
    @ApiResponse(responseCode = "201", description = "Documento subido exitosamente y puesto en revisión")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    public UserDocumentResponseDTO uploadDocument(
            @Parameter(description = "Identificador UUID del tipo de documento", required = true)
            @RequestParam("documentTypeId") UUID documentTypeId,

            @Parameter(description = "Número o serial del documento (opcional)", required = false)
            @RequestParam(value = "documentNumber", required = false) String documentNumber,

            @Parameter(description = "Archivo con la foto o scan frontal del documento (JPG, PNG o PDF, máx 5MB)", required = true)
            @RequestParam("frontFile") MultipartFile frontFile,

            @Parameter(description = "Archivo con la foto o scan trasero del documento (si aplica)", required = false)
            @RequestParam(value = "backFile", required = false) MultipartFile backFile,

            Authentication authentication
    ) {
        return userDocumentService.uploadDocument(
                authentication.getName(),
                documentTypeId,
                documentNumber,
                frontFile,
                backFile
        );
    }
}
