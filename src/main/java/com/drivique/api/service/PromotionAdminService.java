package com.drivique.api.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.drivique.api.config.Auditable;
import com.drivique.api.dto.PromotionRequestDTO;
import com.drivique.api.dto.PromotionResponseDTO;
import com.drivique.api.dto.PromotionVehicleDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Promotion;
import com.drivique.api.model.Vehicle;
import com.drivique.api.model.VehicleImage;
import com.drivique.api.repository.PromotionRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.VehicleCategoryRepository;
import com.drivique.api.repository.VehicleImageRepository;
import com.drivique.api.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.drivique.api.model.Branch;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.BranchUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Gestión (admin) y publicación (cliente) de promociones y cupones. */
@Service
public class PromotionAdminService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final PromotionRepository promotions;
    private final VehicleRepository vehicles;
    private final VehicleCategoryRepository categories;
    private final VehicleImageRepository images;
    private final ReservationRepository reservations;
    private final UserRepository users;

    public PromotionAdminService(PromotionRepository promotions, VehicleRepository vehicles,
                                 VehicleCategoryRepository categories, VehicleImageRepository images,
                                 ReservationRepository reservations, UserRepository users) {
        this.promotions = promotions;
        this.vehicles = vehicles;
        this.categories = categories;
        this.images = images;
        this.reservations = reservations;
        this.users = users;
    }

    @Autowired(required = false)
    private BranchRepository branchRepository;

    @Autowired(required = false)
    private BranchUserRepository branchUserRepository;

    @Transactional(readOnly = true)
    public List<PromotionResponseDTO> listAll() {
        return promotions.findAll().stream()
                .sorted(Comparator.comparing(Promotion::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PromotionResponseDTO> listForUser(String email) {
        if (email == null) {
            return listAll();
        }
        User user = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
        if (user == null) {
            return listAll();
        }
        boolean isSuperAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r.getCode()) || "ADMIN".equalsIgnoreCase(r.getCode()));
        if (isSuperAdmin) {
            return listAll();
        }

        // Branch manager: filter strictly to their branch's promotions!
        UUID branchId = branchUserRepository != null
                ? branchUserRepository.findByUserId(user.getId()).map(com.drivique.api.model.BranchUser::getBranchId).orElse(null)
                : null;

        if (branchId == null) {
            return List.of();
        }

        return promotions.findAll().stream()
                .filter(p -> {
                    if (p.getBranch() != null && branchId.equals(p.getBranch().getId())) {
                        return true;
                    }
                    if (p.getVehicle() != null && p.getVehicle().getCurrentBranch() != null && branchId.equals(p.getVehicle().getCurrentBranch().getId())) {
                        return true;
                    }
                    return false;
                })
                .sorted(Comparator.comparing(Promotion::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .toList();
    }

    /** Promociones vigentes para el cliente; la audiencia (nuevos / frecuentes) se evalúa con sus reservas completadas. */
    @Transactional(readOnly = true)
    public List<PromotionResponseDTO> listPublished(String email) {
        Instant now = Instant.now();
        long completed = completedReservations(email);
        return promotions.findAll().stream()
                .filter(Promotion::isActive)
                .filter(p -> !now.isBefore(p.getStartsAt()) && now.isBefore(p.getEndsAt()))
                .filter(p -> audienceAllows(p.getAudience(), email, completed))
                .sorted(Comparator.comparing(Promotion::isFeatured).reversed().thenComparing(Promotion::getEndsAt))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "INSERT", description = "Creación de promoción o cupón")
    public PromotionResponseDTO create(PromotionRequestDTO request) {
        return create(request, null);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "INSERT", description = "Creación de promoción o cupón")
    public PromotionResponseDTO create(PromotionRequestDTO request, String email) {
        assertCanManagePromotions(email);
        String code = request.code().strip().toUpperCase();
        if (promotions.findByCodeIgnoreCase(code).isPresent()) {
            throw new ConflictException("Ya existe una promoción o cupón con el código " + code);
        }
        Promotion promotion = new Promotion(code, request.offerType(), request.discountType(), request.discountValue(),
                Instant.now(), Instant.now().plusSeconds(1), 1, null, 0);
        apply(promotion, request);
        resolveBranchForPromotion(promotion, request, email);
        Promotion saved = promotions.save(promotion);
        announce();
        return toDto(saved);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Actualización de promoción o cupón")
    public PromotionResponseDTO update(UUID id, PromotionRequestDTO request) {
        return update(id, request, null);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Actualización de promoción o cupón")
    public PromotionResponseDTO update(UUID id, PromotionRequestDTO request, String email) {
        Promotion promotion = find(id);
        assertBranchManagerOwnership(promotion, email);
        String code = request.code().strip().toUpperCase();
        promotions.findByCodeIgnoreCase(code).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Ya existe una promoción o cupón con el código " + code);
        });
        promotion.setCode(code);
        promotion.setOfferType(request.offerType());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        apply(promotion, request);
        resolveBranchForPromotion(promotion, request, email);
        Promotion saved = promotions.save(promotion);
        announce();
        return toDto(saved);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Activación o desactivación de promoción")
    public PromotionResponseDTO toggleActive(UUID id) {
        return toggleActive(id, null);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Activación o desactivación de promoción")
    public PromotionResponseDTO toggleActive(UUID id, String email) {
        Promotion promotion = find(id);
        assertBranchManagerOwnership(promotion, email);
        promotion.setActive(!promotion.isActive());
        Promotion saved = promotions.save(promotion);
        announce();
        return toDto(saved);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Cambio de promoción destacada")
    public PromotionResponseDTO toggleFeatured(UUID id) {
        return toggleFeatured(id, null);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Cambio de promoción destacada")
    public PromotionResponseDTO toggleFeatured(UUID id, String email) {
        Promotion promotion = find(id);
        assertBranchManagerOwnership(promotion, email);
        promotion.setFeatured(!promotion.isFeatured());
        Promotion saved = promotions.save(promotion);
        announce();
        return toDto(saved);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "DELETE", description = "Eliminación de promoción")
    public void delete(UUID id) {
        delete(id, null);
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "DELETE", description = "Eliminación de promoción")
    public void delete(UUID id, String email) {
        Promotion promotion = find(id);
        assertBranchManagerOwnership(promotion, email);
        try {
            promotions.delete(promotion);
            promotions.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("La promoción ya fue usada en reservas; desactívela en lugar de eliminarla.");
        }
    }

    private void apply(Promotion promotion, PromotionRequestDTO request) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        if ("PERCENTAGE".equals(request.discountType()) && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("El porcentaje de descuento no puede superar 100");
        }
        if (request.maxUsesLimit() != null && request.maxUsesLimit() < promotion.getCurrentUsesCount()) {
            throw new IllegalArgumentException("El límite de usos no puede ser menor a los usos ya registrados");
        }
        promotion.setName(request.name().strip());
        promotion.setConditions(request.conditions().strip());
        promotion.setStartsAt(start.atStartOfDay(ZONE).toInstant());
        promotion.setEndsAt(end.atTime(LocalTime.of(23, 59, 59)).atZone(ZONE).toInstant());
        promotion.setMinimumAmount(request.minimumAmount() == null ? BigDecimal.ZERO : request.minimumAmount());
        promotion.setMinimumRentalDays(request.minimumRentalDays() == null ? 1 : request.minimumRentalDays());
        promotion.setMaxUsesLimit(request.maxUsesLimit());
        promotion.setAudience(request.audience() == null ? "ALL" : request.audience());
        promotion.setFeatured(Boolean.TRUE.equals(request.featured()));
        promotion.setActive(request.active() == null || request.active());
        promotion.setVehicle(request.vehicleId() == null ? null : vehicles.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado")));
        promotion.setVehicleCategory(request.vehicleCategoryId() == null ? null : categories.findById(request.vehicleCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada")));
    }

    @Autowired(required = false)
    private PromotionNotificationService promotionNotifications;

    private void announce() {
        if (promotionNotifications != null) {
            try {
                promotionNotifications.notifyPending();
            } catch (Exception ignored) {
                // la tarea programada lo reintenta
            }
        }
    }

    private Promotion find(UUID id) {
        return promotions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
    }

    private long completedReservations(String email) {
        if (email == null) {
            return 0;
        }
        return users.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .map(user -> reservations.countByCustomerIdAndStatusCode(user.getId(), "COMPLETED"))
                .orElse(0L);
    }

    static boolean audienceAllows(String audience, String email, long completed) {
        if (audience == null || "ALL".equals(audience)) {
            return true;
        }
        if (email == null) {
            return false;
        }
        return "NEW".equals(audience) ? completed == 0 : completed >= 2;
    }

    private PromotionVehicleDTO toVehicleDto(Vehicle v) {
        List<String> urls = images.findByVehicleIdOrderBySortOrderAsc(v.getId()).stream().map(VehicleImage::getUrl).limit(3).toList();
        return new PromotionVehicleDTO(v.getId(), v.getBrand().getName() + " " + v.getModel(), v.getCategory().getName(), v.getDailyRate(), urls);
    }

    private void assertCanManagePromotions(String email) {
        if (email == null) return;
        User user = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
        if (user == null) return;
        boolean isSuperAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r.getCode()) || "ADMIN".equalsIgnoreCase(r.getCode()));
        if (isSuperAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El administrador general solo puede visualizar ofertas y cupones. La creación y edición es gestionada por cada encargado de sucursal.");
        }
    }

    private void assertBranchManagerOwnership(Promotion promotion, String email) {
        if (email == null) return;
        User user = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
        if (user == null) return;
        boolean isSuperAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r.getCode()) || "ADMIN".equalsIgnoreCase(r.getCode()));
        if (isSuperAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El administrador general solo puede visualizar ofertas y cupones. La gestión operativa corresponde al encargado de la sucursal.");
        }
        if (branchUserRepository != null) {
            UUID managerBranchId = branchUserRepository.findByUserId(user.getId())
                    .map(com.drivique.api.model.BranchUser::getBranchId)
                    .orElse(null);
            if (managerBranchId != null) {
                if (promotion.getBranch() != null && !managerBranchId.equals(promotion.getBranch().getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para modificar promociones de otra sucursal.");
                }
                if (promotion.getVehicle() != null && promotion.getVehicle().getCurrentBranch() != null
                        && !managerBranchId.equals(promotion.getVehicle().getCurrentBranch().getId())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El vehículo seleccionado pertenece a otra sucursal.");
                }
            }
        }
    }

    private void resolveBranchForPromotion(Promotion promotion, PromotionRequestDTO request, String email) {
        UUID branchId = request.branchId();
        if (branchId == null && email != null && branchUserRepository != null) {
            User user = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
            if (user != null) {
                branchId = branchUserRepository.findByUserId(user.getId())
                        .map(com.drivique.api.model.BranchUser::getBranchId)
                        .orElse(null);
            }
        }
        if (branchId == null && promotion.getVehicle() != null && promotion.getVehicle().getCurrentBranch() != null) {
            branchId = promotion.getVehicle().getCurrentBranch().getId();
        }
        if (branchId != null && branchRepository != null) {
            Branch b = branchRepository.findById(branchId).orElse(null);
            promotion.setBranch(b);
        }
    }

    private PromotionResponseDTO toDto(Promotion p) {
        Vehicle vehicle = p.getVehicle();
        List<Vehicle> targets;
        if (vehicle != null) {
            targets = List.of(vehicle);
        } else if (p.getVehicleCategory() != null) {
            targets = vehicles.findTop2ByCategoryIdAndActiveTrueOrderByModelAsc(p.getVehicleCategory().getId());
        } else {
            targets = vehicles.findTop1ByActiveTrueOrderByModelAsc();
        }
        List<PromotionVehicleDTO> vehicleDtos = targets.stream().map(this::toVehicleDto).toList();

        UUID branchId = p.getBranch() != null ? p.getBranch().getId() : (vehicle != null && vehicle.getCurrentBranch() != null ? vehicle.getCurrentBranch().getId() : null);
        String branchName = p.getBranch() != null ? p.getBranch().getName() : (vehicle != null && vehicle.getCurrentBranch() != null ? vehicle.getCurrentBranch().getName() : null);

        return new PromotionResponseDTO(
                p.getId(), p.getCode(), p.getName(), p.getConditions(), p.getOfferType(), p.getDiscountType(),
                p.getDiscountValue(), p.getStartsAt().atZone(ZONE).toLocalDate(), p.getEndsAt().atZone(ZONE).toLocalDate(),
                p.getMinimumAmount(), p.getMinimumRentalDays(), p.getMaxUsesLimit(), p.getCurrentUsesCount(),
                p.getVehicleCategory() == null ? null : p.getVehicleCategory().getId(),
                p.getVehicleCategory() == null ? null : p.getVehicleCategory().getName(),
                vehicle == null ? null : vehicle.getId(),
                vehicle == null ? null : vehicle.getBrand().getName() + " " + vehicle.getModel(),
                vehicleDtos, p.getAudience(), p.isFeatured(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt(),
                branchId, branchName);
    }
}
