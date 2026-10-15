package com.drivique.api.service;

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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public List<PromotionResponseDTO> listAll() {
        return promotions.findAll().stream()
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
        String code = request.code().strip().toUpperCase();
        if (promotions.findByCodeIgnoreCase(code).isPresent()) {
            throw new ConflictException("Ya existe una promoción o cupón con el código " + code);
        }
        Promotion promotion = new Promotion(code, request.offerType(), request.discountType(), request.discountValue(),
                Instant.now(), Instant.now().plusSeconds(1), 1, null, 0);
        apply(promotion, request);
        return toDto(promotions.save(promotion));
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Actualización de promoción o cupón")
    public PromotionResponseDTO update(UUID id, PromotionRequestDTO request) {
        Promotion promotion = find(id);
        String code = request.code().strip().toUpperCase();
        promotions.findByCodeIgnoreCase(code).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Ya existe una promoción o cupón con el código " + code);
        });
        promotion.setCode(code);
        promotion.setOfferType(request.offerType());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        apply(promotion, request);
        return toDto(promotions.save(promotion));
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Activación o desactivación de promoción")
    public PromotionResponseDTO toggleActive(UUID id) {
        Promotion promotion = find(id);
        promotion.setActive(!promotion.isActive());
        return toDto(promotions.save(promotion));
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "UPDATE", description = "Cambio de promoción destacada")
    public PromotionResponseDTO toggleFeatured(UUID id) {
        Promotion promotion = find(id);
        promotion.setFeatured(!promotion.isFeatured());
        return toDto(promotions.save(promotion));
    }

    @Transactional
    @CacheEvict(value = "featuredPromotions", allEntries = true)
    @Auditable(domain = "PROMOTIONS", entity = "Promotion", action = "DELETE", description = "Eliminación de promoción")
    public void delete(UUID id) {
        Promotion promotion = find(id);
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
        return new PromotionResponseDTO(
                p.getId(), p.getCode(), p.getName(), p.getConditions(), p.getOfferType(), p.getDiscountType(),
                p.getDiscountValue(), p.getStartsAt().atZone(ZONE).toLocalDate(), p.getEndsAt().atZone(ZONE).toLocalDate(),
                p.getMinimumAmount(), p.getMinimumRentalDays(), p.getMaxUsesLimit(), p.getCurrentUsesCount(),
                p.getVehicleCategory() == null ? null : p.getVehicleCategory().getId(),
                p.getVehicleCategory() == null ? null : p.getVehicleCategory().getName(),
                vehicle == null ? null : vehicle.getId(),
                vehicle == null ? null : vehicle.getBrand().getName() + " " + vehicle.getModel(),
                vehicleDtos, p.getAudience(), p.isFeatured(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
