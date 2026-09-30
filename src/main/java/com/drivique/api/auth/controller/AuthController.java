package com.drivique.api.auth.controller;

import com.drivique.api.auth.dto.AuthResponseDTO;
import com.drivique.api.auth.dto.LoginRequestDTO;
import com.drivique.api.auth.dto.RefreshTokenRequestDTO;
import com.drivique.api.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "Inicio de sesión, rotación de tokens y cierre de sesión")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Valida credenciales y emite tokens JWT y de refresco. Bloquea la cuenta tras 5 intentos fallidos.")
    @ApiResponse(responseCode = "200", description = "Autenticación exitosa")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "423", description = "Cuenta bloqueada por múltiples intentos fallidos")
    public AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest servletRequest) {
        String ipAddress = extractIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        return authService.login(request, ipAddress, userAgent);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar tokens", description = "Valida y rota el refresh token, emitiendo un nuevo Access Token JWT.")
    @ApiResponse(responseCode = "200", description = "Tokens renovados exitosamente")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    public AuthResponseDTO refresh(@Valid @RequestBody RefreshTokenRequestDTO request, HttpServletRequest servletRequest) {
        String ipAddress = extractIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        return authService.refresh(request, ipAddress, userAgent);
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca el refresh token activo en el sistema.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada exitosamente")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequestDTO request) {
        if (request != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    private String extractIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
