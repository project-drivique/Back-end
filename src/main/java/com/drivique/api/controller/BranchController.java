package com.drivique.api.controller;
import com.drivique.api.dto.*;
import com.drivique.api.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/v1/branches")
public class BranchController {
 private final BranchService service; public BranchController(BranchService service) { this.service = service; }
 @GetMapping @Operation(summary="Consultar sedes activas") public List<BranchResponseDTO> active() { return service.active(); }
 @GetMapping("/{id}") public BranchResponseDTO detail(@PathVariable UUID id) { return service.detail(id); }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')") public BranchResponseDTO create(@Valid @RequestBody BranchRequestDTO input) { return service.create(input); }
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')") public BranchResponseDTO update(@PathVariable UUID id, @Valid @RequestBody BranchRequestDTO input) { return service.update(id, input); }
 @PatchMapping("/{id}/toggle-status") @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')") public BranchResponseDTO toggle(@PathVariable UUID id) { return service.toggle(id); }
}
