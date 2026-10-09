package com.drivique.api.controller;

import com.drivique.api.dto.*;
import com.drivique.api.service.SocialAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Social Authentication", description = "Inicio de sesión e integración OAuth 2.0 / OIDC con Google y Facebook (HU-INT-05)")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    public SocialAuthController(SocialAuthService socialAuthService) {
        this.socialAuthService = socialAuthService;
    }

    @PostMapping("/social/login")
    @Operation(summary = "Inicio de sesión social", description = "Valida token OIDC / Authorization Code con PKCE para Google y Facebook, auto-registra al usuario o vincula su cuenta existente, y emite tokens JWT de Drivique.")
    @ApiResponse(responseCode = "200", description = "Autenticación social exitosa")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "423", description = "Cuenta bloqueada")
    public AuthResponseDTO socialLogin(@Valid @RequestBody SocialLoginRequestDTO request, HttpServletRequest servletRequest) {
        String ipAddress = extractIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        return socialAuthService.login(request, ipAddress, userAgent);
    }

    @PostMapping("/google")
    @Operation(summary = "Inicio de sesión rápido con Google", description = "Endpoint de conveniencia para autenticación con Google Identity Services.")
    @ApiResponse(responseCode = "200", description = "Autenticación con Google exitosa")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    public AuthResponseDTO googleLogin(@RequestBody SocialLoginRequestDTO request, HttpServletRequest servletRequest) {
        SocialLoginRequestDTO fullRequest = new SocialLoginRequestDTO(
                "GOOGLE",
                request.idToken(),
                request.accessToken(),
                request.authCode(),
                request.codeVerifier(),
                request.redirectUri(),
                request.nonce(),
                request.deviceInfo(),
                request.email(),
                request.firstName(),
                request.lastName()
        );
        String ipAddress = extractIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        return socialAuthService.login(fullRequest, ipAddress, userAgent);
    }

    @PostMapping("/facebook")
    @Operation(summary = "Inicio de sesión rápido con Facebook", description = "Endpoint de conveniencia para autenticación con Facebook SDK.")
    @ApiResponse(responseCode = "200", description = "Autenticación con Facebook exitosa")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    public AuthResponseDTO facebookLogin(@RequestBody SocialLoginRequestDTO request, HttpServletRequest servletRequest) {
        SocialLoginRequestDTO fullRequest = new SocialLoginRequestDTO(
                "FACEBOOK",
                request.idToken(),
                request.accessToken(),
                request.authCode(),
                request.codeVerifier(),
                request.redirectUri(),
                request.nonce(),
                request.deviceInfo(),
                request.email(),
                request.firstName(),
                request.lastName()
        );
        String ipAddress = extractIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        return socialAuthService.login(fullRequest, ipAddress, userAgent);
    }

    @PostMapping("/social/link")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Vincular cuenta social", description = "Vincula un proveedor OAuth (Google/Facebook) al perfil del usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Cuenta social vinculada exitosamente")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "409", description = "La cuenta social ya está asociada a otro usuario")
    public SocialAccountResponseDTO linkAccount(@Valid @RequestBody SocialLinkRequestDTO request, Authentication authentication) {
        return socialAuthService.linkAccount(authentication.getName(), request);
    }

    @GetMapping("/social/accounts")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Listar cuentas sociales vinculadas", description = "Obtiene los proveedores sociales asociados al usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Cuentas sociales listadas exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public List<SocialAccountResponseDTO> getLinkedAccounts(Authentication authentication) {
        return socialAuthService.getLinkedAccounts(authentication.getName());
    }

    @DeleteMapping("/social/{provider}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Desvincular cuenta social", description = "Elimina la vinculación del proveedor social especificado.")
    @ApiResponse(responseCode = "200", description = "Cuenta social desvinculada exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Proveedor no vinculado")
    public MessageResponseDTO unlinkAccount(@PathVariable String provider, Authentication authentication) {
        return socialAuthService.unlinkAccount(authentication.getName(), provider);
    }

    private String extractIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
