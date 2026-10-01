package com.drivique.api.controller;

import com.drivique.api.dto.ConsentResponseDTO;
import com.drivique.api.dto.ConsentStatusResponseDTO;
import com.drivique.api.dto.CreateConsentRequestDTO;
import com.drivique.api.service.ClientIpAddressResolver;
import com.drivique.api.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@Tag(name = "Legal consents", description = "Consentimientos legales del usuario autenticado")
@SecurityRequirement(name = "bearerAuth")
public class ConsentController {
    private final ConsentService service;
    private final ClientIpAddressResolver ips;
    public ConsentController(ConsentService service, ClientIpAddressResolver ips) { this.service = service; this.ips = ips; }
    @PostMapping("/consents") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar consentimiento legal", description = "La IP se obtiene del socket de la solicitud; no se acepta desde el cliente.")
    @ApiResponse(responseCode = "201", description = "Consentimiento inmutable registrado")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    public ConsentResponseDTO register(@Valid @RequestBody CreateConsentRequestDTO request,
            Authentication authentication, HttpServletRequest servletRequest) {
        return service.register(authentication.getName(), request, ips.resolve(servletRequest));
    }
    @GetMapping("/users/me/consents")
    @Operation(summary = "Auditar mis consentimientos")
    public List<ConsentResponseDTO> mine(Authentication authentication) { return service.mine(authentication.getName()); }
    @GetMapping("/consents/status")
    @Operation(summary = "Consultar consentimientos pendientes", description = "Compara el historial con las versiones requeridas configuradas por el ambiente.")
    public ConsentStatusResponseDTO status(Authentication authentication) { return service.status(authentication.getName()); }
}
