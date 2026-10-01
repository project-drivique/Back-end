package com.drivique.api.controller;

import com.drivique.api.dto.CityRequestDTO;
import com.drivique.api.dto.CityResponseDTO;
import com.drivique.api.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cities")
@Tag(name = "Locations", description = "División territorial para reservas")
public class CityController {
    private final LocationService service;

    public CityController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Consultar ciudades activas")
    public List<CityResponseDTO> cities() {
        return service.cities();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Crear ciudad", description = "Requiere BRANCH_ADMIN o SUPER_ADMIN")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    public CityResponseDTO create(@Valid @RequestBody CityRequestDTO input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Actualizar ciudad", description = "Requiere BRANCH_ADMIN o SUPER_ADMIN")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    public CityResponseDTO update(@PathVariable UUID id, @Valid @RequestBody CityRequestDTO input) {
        return service.update(id, input);
    }
}
