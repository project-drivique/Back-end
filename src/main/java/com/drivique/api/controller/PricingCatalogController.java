package com.drivique.api.controller;
import com.drivique.api.dto.*;
import com.drivique.api.service.PricingCatalogService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/v1") public class PricingCatalogController {
    private final PricingCatalogService service; public PricingCatalogController(PricingCatalogService service) { this.service = service; }
    @GetMapping("/additional-services") public List<AdditionalServiceResponseDTO> services() { return service.services(); }
    @GetMapping("/insurance-coverages") public List<InsuranceCoverageResponseDTO> coverages() { return service.coverages(); }
    @PostMapping("/admin/additional-services") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPER_ADMIN')") public AdditionalServiceResponseDTO createService(@Valid @RequestBody AdditionalServiceRequestDTO input) { return service.create(input); }
    @PutMapping("/admin/additional-services/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") public AdditionalServiceResponseDTO updateService(@PathVariable UUID id, @Valid @RequestBody AdditionalServiceRequestDTO input) { return service.update(id, input); }
    @PostMapping("/admin/insurance-coverages") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SUPER_ADMIN')") public InsuranceCoverageResponseDTO createCoverage(@Valid @RequestBody InsuranceCoverageRequestDTO input) { return service.create(input); }
    @PutMapping("/admin/insurance-coverages/{id}") @PreAuthorize("hasRole('SUPER_ADMIN')") public InsuranceCoverageResponseDTO updateCoverage(@PathVariable UUID id, @Valid @RequestBody InsuranceCoverageRequestDTO input) { return service.update(id, input); }
}
