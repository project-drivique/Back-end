package com.drivique.api.controller;

import com.drivique.api.dto.FeaturedPromotionResponseDTO;
import com.drivique.api.dto.PromotionResponseDTO;
import com.drivique.api.dto.PromotionValidationRequestDTO;
import com.drivique.api.dto.PromotionValidationResponseDTO;
import com.drivique.api.service.PromotionAdminService;
import com.drivique.api.service.PromotionValidationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/promotions")
public class PromotionController {

    private final PromotionValidationService service;
    private final PromotionAdminService adminService;

    public PromotionController(PromotionValidationService service, PromotionAdminService adminService) {
        this.service = service;
        this.adminService = adminService;
    }

    @PostMapping("/validate")
    public PromotionValidationResponseDTO validate(@Valid @RequestBody PromotionValidationRequestDTO input,
                                                   Authentication authentication) {
        return service.validate(input, authentication.getName());
    }

    @GetMapping("/featured")
    public List<FeaturedPromotionResponseDTO> featured() {
        return service.featured();
    }

    /** Promociones y cupones vigentes; si hay sesión, se filtran por la audiencia del usuario. */
    @GetMapping("/published")
    public List<PromotionResponseDTO> published(Authentication authentication) {
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return adminService.listPublished(signedIn ? authentication.getName() : null);
    }
}
