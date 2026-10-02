package com.drivique.api.controller;

import com.drivique.api.dto.*;
import com.drivique.api.service.PricingEngineService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/v1/pricing")
public class PricingController {
    private final PricingEngineService service;
    public PricingController(PricingEngineService service) { this.service=service; }
    @PostMapping("/quote") public PriceQuoteResponseDTO quote(@Valid @RequestBody PriceQuoteRequestDTO input, Authentication authentication) { return service.quote(input,authentication.getName()); }
}
