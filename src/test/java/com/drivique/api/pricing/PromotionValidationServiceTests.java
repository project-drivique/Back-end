package com.drivique.api.pricing;

import com.drivique.api.dto.FeaturedPromotionResponseDTO;
import com.drivique.api.dto.PromotionValidationRequestDTO;
import com.drivique.api.dto.PromotionValidationResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Promotion;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.drivique.api.model.VehicleCategory;
import com.drivique.api.repository.PromotionRepository;
import com.drivique.api.repository.UserCouponUsageRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.VehicleRepository;
import com.drivique.api.service.PromotionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionValidationServiceTests {

    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final UserCouponUsageRepository usages = mock(UserCouponUsageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final PromotionValidationService service = new PromotionValidationService(promotions, usages, users, vehicles);

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final User mockUser = mock(User.class);
    private final Vehicle mockVehicle = mock(Vehicle.class);

    @BeforeEach
    void setup() {
        VehicleCategory category = mock(VehicleCategory.class);
        when(category.getId()).thenReturn(categoryId);
        when(mockVehicle.getCategory()).thenReturn(category);
        when(mockVehicle.getDailyRate()).thenReturn(new BigDecimal("100000"));
        when(vehicles.findById(vehicleId)).thenReturn(Optional.of(mockVehicle));
        when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("user@drivique.com")).thenReturn(Optional.of(mockUser));
    }

    @Test
    void rejectsNotFoundPromotion() {
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Promotion not found");
    }

    @Test
    void rejectsNotFoundVehicle() {
        when(vehicles.findById(vehicleId)).thenReturn(Optional.empty());
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vehicle not found");
    }

    @Test
    void rejectsCategoryMismatch() {
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        PromotionValidationRequestDTO req = new PromotionValidationRequestDTO("SAVE10", vehicleId, UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(2));
        assertThatThrownBy(() -> service.validate(req, "user@drivique.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vehicle does not match category");
    }

    @Test
    void rejectsInactivePromotion() {
        Promotion p = promotion(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, null, 0);
        p.setActive(false);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Promotion is not active");
    }

    @Test
    void rejectsFuturePromotion() {
        Promotion p = promotion(Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(5, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsExpiredPromotion() {
        Promotion p = promotion(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsExhaustedPromotion() {
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, 1, 1);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsInsufficientRentalDays() {
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 3, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsUserNotFound() {
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));
        when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@drivique.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(request(2), "unknown@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void rejectsUserAlreadyUsedPromotion() {
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));
        when(usages.existsByPromotionAndUser(p, mockUser)).thenReturn(true);

        assertThatThrownBy(() -> service.validate(request(2), "user@drivique.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Promotion already used by this user");
    }

    @Test
    void calculatesPercentageDiscount() {
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));

        PromotionValidationResponseDTO response = service.validate(request(2), "user@drivique.com");
        assertThat(response.discountAmount()).isEqualByComparingTo("20000.00");
        assertThat(response.baseAmount()).isEqualByComparingTo("200000.00");
    }

    @Test
    void calculatesFixedDiscount() {
        Promotion p = new Promotion("FIXED50", "COUPON", "FIXED", new BigDecimal("50000"), Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByCodeIgnoreCase("FIXED50")).thenReturn(Optional.of(p));

        PromotionValidationRequestDTO req = new PromotionValidationRequestDTO("FIXED50", vehicleId, categoryId, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));
        PromotionValidationResponseDTO response = service.validate(req, "user@drivique.com");

        assertThat(response.discountAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void returnsFeaturedPromotions() {
        Promotion p = promotion(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(5, ChronoUnit.DAYS), 1, null, 0);
        when(promotions.findByActiveTrueAndOfferTypeAndStartsAtLessThanEqualAndEndsAtAfterOrderByEndsAtAsc(eq("PROMOTION"), any(), any()))
                .thenReturn(List.of(p));

        List<FeaturedPromotionResponseDTO> result = service.featured();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("SAVE10");
    }

    private PromotionValidationRequestDTO request(int days) {
        LocalDate start = LocalDate.now().plusDays(2);
        return new PromotionValidationRequestDTO("SAVE10", vehicleId, categoryId, start, start.plusDays(days));
    }

    private Promotion promotion(Instant starts, Instant ends, int minimum, Integer max, int used) {
        return new Promotion("SAVE10", "COUPON", "PERCENTAGE", new BigDecimal("10"), starts, ends, minimum, max, used);
    }
}
