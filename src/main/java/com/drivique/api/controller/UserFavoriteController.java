package com.drivique.api.controller;

import com.drivique.api.dto.VehicleCardResponseDTO;
import com.drivique.api.service.UserFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users/me/favorites")
@Tag(name = "User Favorites", description = "Gestión de vehículos favoritos del usuario autenticado")
@SecurityRequirement(name = "bearerAuth")
public class UserFavoriteController {

    private final UserFavoriteService favoriteService;

    public UserFavoriteController(UserFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    @Operation(summary = "Listar vehículos favoritos", description = "Obtiene la lista de vehículos marcados como favoritos por el usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Lista de favoritos obtenida exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public List<VehicleCardResponseDTO> getFavorites(Authentication authentication) {
        return favoriteService.listFavorites(authentication.getName());
    }

    @PostMapping("/{vehicleId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar vehículo a favoritos", description = "Guarda un vehículo en la lista de favoritos del usuario autenticado.")
    @ApiResponse(responseCode = "201", description = "Vehículo agregado a favoritos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Vehículo no encontrado")
    public void addFavorite(@PathVariable UUID vehicleId, Authentication authentication) {
        favoriteService.addFavorite(authentication.getName(), vehicleId);
    }

    @DeleteMapping("/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover vehículo de favoritos", description = "Elimina un vehículo de la lista de favoritos del usuario autenticado.")
    @ApiResponse(responseCode = "204", description = "Vehículo eliminado de favoritos exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Usuario o vehículo no encontrado")
    public void removeFavorite(@PathVariable UUID vehicleId, Authentication authentication) {
        favoriteService.removeFavorite(authentication.getName(), vehicleId);
    }
}
