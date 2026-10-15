package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.BranchReviewAdminDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.BranchReview;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchReviewRepository;
import com.drivique.api.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Gestión de reseñas de sucursal: el encargado solo ve y responde las de su sucursal. */
@Service
public class BranchReviewAdminService {

    private final BranchReviewRepository reviews;
    private final UserRepository users;
    private final StaffBranchScope scope;

    public BranchReviewAdminService(BranchReviewRepository reviews, UserRepository users, StaffBranchScope scope) {
        this.reviews = reviews;
        this.users = users;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<BranchReviewAdminDTO> list(String email) {
        Optional<UUID> branchId = scope.forStaff(email);
        List<BranchReview> found = branchId.isPresent()
                ? reviews.findByBranchIdOrderByCreatedAtDesc(branchId.get())
                : reviews.findAll().stream().sorted(Comparator.comparing(BranchReview::getCreatedAt).reversed()).toList();
        return found.stream().map(this::toDto).toList();
    }

    @Transactional
    @Auditable(domain = "RESERVATIONS", entity = "BranchReview", action = "UPDATE", description = "Respuesta a una reseña de sucursal")
    public BranchReviewAdminDTO reply(String email, UUID id, String text) {
        BranchReview review = own(email, id);
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La respuesta no puede estar vacía.");
        }
        User author = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        review.reply(text.strip(), author);
        return toDto(reviews.saveAndFlush(review));
    }

    @Transactional
    @Auditable(domain = "RESERVATIONS", entity = "BranchReview", action = "DELETE", description = "Eliminación de una reseña de sucursal")
    public void delete(String email, UUID id) {
        reviews.delete(own(email, id));
    }

    private BranchReview own(String email, UUID id) {
        BranchReview review = reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reseña no encontrada"));
        Optional<UUID> branchId = scope.forStaff(email);
        if (branchId.isPresent() && !branchId.get().equals(review.getBranch().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La reseña pertenece a otra sucursal.");
        }
        return review;
    }

    private BranchReviewAdminDTO toDto(BranchReview r) {
        var vehicle = r.getReservation().getVehicle();
        return new BranchReviewAdminDTO(r.getId(), r.getBranch().getId(), r.getBranch().getName(), r.getUser().getFullName(),
                r.getReservation().getCode(), vehicle == null ? null : vehicle.getBrand().getName() + " " + vehicle.getModel(),
                r.getRating(), r.getComment(), r.getManagerReply(), r.getRepliedAt(), r.getCreatedAt());
    }
}
