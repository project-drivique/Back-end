package com.drivique.api.controller;

import com.drivique.api.dto.CityResponseDTO;
import com.drivique.api.dto.DepartmentResponseDTO;
import com.drivique.api.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/departments")
@Tag(name = "Locations", description = "División territorial para reservas")
public class DepartmentController {
    private final LocationService service;

    public DepartmentController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Consultar departamentos activos")
    public List<DepartmentResponseDTO> departments() {
        return service.departments();
    }

    @GetMapping("/{id}/cities")
    @Operation(summary = "Consultar ciudades activas de un departamento")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    public List<CityResponseDTO> cities(@PathVariable UUID id) {
        return service.citiesByDepartment(id);
    }
}
