package com.drivique.api.controller;

import com.drivique.api.dto.DeliveryDriverDTO;
import com.drivique.api.dto.DeliveryDriverRequestDTO;
import com.drivique.api.dto.DeliveryPointResponseDTO;
import com.drivique.api.service.DeliveryLogisticsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Delivery Logistics", description = "Conductores de domicilio y entregas a domicilio")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'EMPLOYEE', 'SUPER_ADMIN')")
public class DeliveryLogisticsController {

    public record AssignDriverRequest(@NotNull UUID driverId) {}

    public record CompleteDeliveryRequest(@NotBlank String pin) {}

    private final DeliveryLogisticsService service;

    public DeliveryLogisticsController(DeliveryLogisticsService service) {
        this.service = service;
    }

    @GetMapping("/delivery-drivers")
    public List<DeliveryDriverDTO> list(Authentication authentication) {
        return service.listDrivers(authentication.getName());
    }

    @PostMapping("/delivery-drivers")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryDriverDTO create(@Valid @RequestBody DeliveryDriverRequestDTO request, Authentication authentication) {
        return service.createDriver(authentication.getName(), request);
    }

    @PutMapping("/delivery-drivers/{id}")
    public DeliveryDriverDTO update(@PathVariable UUID id, @Valid @RequestBody DeliveryDriverRequestDTO request, Authentication authentication) {
        return service.updateDriver(authentication.getName(), id, request);
    }

    @DeleteMapping("/delivery-drivers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        service.deleteDriver(authentication.getName(), id);
    }

    @PostMapping("/deliveries/{reservationId}/{pointType}/assign")
    public DeliveryPointResponseDTO assign(@PathVariable UUID reservationId, @PathVariable String pointType,
                                           @Valid @RequestBody AssignDriverRequest request, Authentication authentication) {
        return service.assign(authentication.getName(), reservationId, pointType, request.driverId());
    }

    @PostMapping("/deliveries/{reservationId}/{pointType}/complete")
    public DeliveryPointResponseDTO complete(@PathVariable UUID reservationId, @PathVariable String pointType,
                                             @Valid @RequestBody CompleteDeliveryRequest request, Authentication authentication) {
        return service.complete(authentication.getName(), reservationId, pointType, request.pin());
    }
}
