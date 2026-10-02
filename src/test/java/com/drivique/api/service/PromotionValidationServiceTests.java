package com.drivique.api.service;

import com.drivique.api.dto.PromotionValidationRequestDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PromotionValidationServiceTests {
 private PromotionRepository promotions=mock(PromotionRepository.class); private UserCouponUsageRepository usages=mock(UserCouponUsageRepository.class); private UserRepository users=mock(UserRepository.class); private VehicleRepository vehicles=mock(VehicleRepository.class); private PromotionValidationService service=new PromotionValidationService(promotions,usages,users,vehicles); private UUID vehicleId=UUID.randomUUID(), categoryId=UUID.randomUUID();
 @BeforeEach void setup(){Vehicle vehicle=mock(Vehicle.class);VehicleCategory category=mock(VehicleCategory.class);when(category.getId()).thenReturn(categoryId);when(vehicle.getCategory()).thenReturn(category);when(vehicle.getDailyRate()).thenReturn(new BigDecimal("100000"));when(vehicles.findById(vehicleId)).thenReturn(Optional.of(vehicle));when(users.findByEmailIgnoreCaseAndDeletedAtIsNull("user@drivique.com")).thenReturn(Optional.of(mock(User.class)));}
 @Test void rejectsExpiredPromotion(){Promotion p=promotion(Instant.now().minus(2,ChronoUnit.DAYS),Instant.now().minus(1,ChronoUnit.DAYS),1,null,0);when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));assertThatThrownBy(()->service.validate(request(2),"user@drivique.com")).isInstanceOf(ConflictException.class);}
 @Test void rejectsExhaustedPromotion(){Promotion p=promotion(Instant.now().minus(1,ChronoUnit.DAYS),Instant.now().plus(1,ChronoUnit.DAYS),1,1,1);when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));assertThatThrownBy(()->service.validate(request(2),"user@drivique.com")).isInstanceOf(ConflictException.class);}
 @Test void rejectsInsufficientRentalDays(){Promotion p=promotion(Instant.now().minus(1,ChronoUnit.DAYS),Instant.now().plus(1,ChronoUnit.DAYS),3,null,0);when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));assertThatThrownBy(()->service.validate(request(2),"user@drivique.com")).isInstanceOf(ConflictException.class);}
 @Test void calculatesPercentageDiscount(){Promotion p=promotion(Instant.now().minus(1,ChronoUnit.DAYS),Instant.now().plus(1,ChronoUnit.DAYS),1,null,0);when(promotions.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(p));assertThat(service.validate(request(2),"user@drivique.com").discountAmount()).isEqualByComparingTo("20000.00");}
 private PromotionValidationRequestDTO request(int days){LocalDate start=LocalDate.now().plusDays(2);return new PromotionValidationRequestDTO("SAVE10",vehicleId,categoryId,start,start.plusDays(days));}
 private Promotion promotion(Instant starts,Instant ends,int minimum,Integer max,int used){return new Promotion("SAVE10","COUPON","PERCENTAGE",new BigDecimal("10"),starts,ends,minimum,max,used);}
}
