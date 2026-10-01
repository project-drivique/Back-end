package com.drivique.api.controller;
import com.drivique.api.dto.*; import com.drivique.api.service.FleetCatalogService; import jakarta.validation.Valid; import java.util.*; import org.springframework.http.HttpStatus; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1") public class FleetCatalogController {
 private final FleetCatalogService service; public FleetCatalogController(FleetCatalogService s){service=s;}
 @GetMapping("/vehicle-brands") public List<TechnicalCatalogResponseDTO> brands(){return service.brands();}
 @GetMapping("/transmission-types") public List<TechnicalCatalogResponseDTO> transmissions(){return service.transmissions();}
 @GetMapping("/fuel-types") public List<TechnicalCatalogResponseDTO> fuels(){return service.fuels();}
 @GetMapping("/vehicle-statuses") public List<TechnicalCatalogResponseDTO> statuses(){return service.statuses();}
 @PostMapping("/vehicle-brands") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPER_ADMIN')") public TechnicalCatalogResponseDTO create(@Valid @RequestBody VehicleBrandRequestDTO input){return service.create(input);}
 @PutMapping("/vehicle-brands/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") public TechnicalCatalogResponseDTO update(@PathVariable UUID id,@Valid @RequestBody VehicleBrandRequestDTO input){return service.update(id,input);}
}
