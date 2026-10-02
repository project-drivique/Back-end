package com.drivique.api.pricing;

import com.drivique.api.service.*;

import com.drivique.api.dto.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PricingEngineServiceTests {
 private final VehicleRepository vehicles=mock(VehicleRepository.class); private final InsuranceCoverageRepository coverages=mock(InsuranceCoverageRepository.class); private final MileagePlanRepository plans=mock(MileagePlanRepository.class); private final AdditionalServiceRepository extras=mock(AdditionalServiceRepository.class); private final PromotionValidationService promotions=mock(PromotionValidationService.class); private final PricingEngineService service=new PricingEngineService(vehicles,coverages,plans,extras,promotions); private final UUID vehicleId=UUID.randomUUID(),insuranceId=UUID.randomUUID(),planId=UUID.randomUUID(),extraId=UUID.randomUUID();
 @BeforeEach void setup(){Vehicle vehicle=mock(Vehicle.class);VehicleCategory category=mock(VehicleCategory.class);when(category.getId()).thenReturn(UUID.randomUUID());when(category.getSecurityDeposit()).thenReturn(new BigDecimal("500000"));when(vehicle.getId()).thenReturn(vehicleId);when(vehicle.getDailyRate()).thenReturn(new BigDecimal("100000"));when(vehicle.getCategory()).thenReturn(category);when(vehicles.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicle));InsuranceCoverage coverage=mock(InsuranceCoverage.class);when(coverage.isActive()).thenReturn(true);when(coverage.getDailyRate()).thenReturn(new BigDecimal("20000"));when(coverages.findById(insuranceId)).thenReturn(Optional.of(coverage));MileagePlan plan=mock(MileagePlan.class);when(plan.isActive()).thenReturn(true);when(plan.getDailyRate()).thenReturn(new BigDecimal("10000"));when(plans.findById(planId)).thenReturn(Optional.of(plan));AdditionalService extra=mock(AdditionalService.class);when(extra.isActive()).thenReturn(true);when(extra.getDailyRate()).thenReturn(new BigDecimal("5000"));when(extras.findById(extraId)).thenReturn(Optional.of(extra));}
 @Test void calculatesCompleteQuote(){PriceQuoteResponseDTO quote=service.quote(request(LocalDate.now(),LocalDate.now().plusDays(2)),"user@drivique.com");assertThat(quote.rentalDays()).isEqualTo(2);assertThat(quote.vehicleSubtotal()).isEqualByComparingTo("200000");assertThat(quote.estimatedTotal()).isEqualByComparingTo("270000");assertThat(quote.refundableSecurityDeposit()).isEqualByComparingTo("500000");}
 @Test void rejectsInvertedDates(){assertThatThrownBy(()->service.quote(request(LocalDate.now().plusDays(2),LocalDate.now()),"user@drivique.com")).isInstanceOf(IllegalArgumentException.class);}
 private PriceQuoteRequestDTO request(LocalDate pickup,LocalDate returned){return new PriceQuoteRequestDTO(vehicleId,pickup,returned,insuranceId,planId,List.of(extraId),null);}
}
