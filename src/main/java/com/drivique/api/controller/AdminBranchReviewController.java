package com.drivique.api.controller;

import com.drivique.api.dto.BranchReviewAdminDTO;
import com.drivique.api.service.BranchReviewAdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/reviews/branches")
@Tag(name = "Admin Branch Reviews", description = "Reseñas de sucursal: consulta, respuesta y moderación")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'EMPLOYEE', 'SUPER_ADMIN')")
public class AdminBranchReviewController {

    public record ReplyRequest(@NotBlank @Size(max = 1000) String reply) {}

    private final BranchReviewAdminService service;

    public AdminBranchReviewController(BranchReviewAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<BranchReviewAdminDTO> list(Authentication authentication) {
        return service.list(authentication.getName());
    }

    @PatchMapping("/{id}/reply")
    public BranchReviewAdminDTO reply(@PathVariable UUID id, @Valid @RequestBody ReplyRequest request, Authentication authentication) {
        return service.reply(authentication.getName(), id, request.reply());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        service.delete(authentication.getName(), id);
    }
}
