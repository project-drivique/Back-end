package com.drivique.api.pricing;

import com.drivique.api.dto.PriceQuoteRequestDTO;
import com.drivique.api.dto.PriceQuoteResponseDTO;
import com.drivique.api.dto.PromotionValidationRequestDTO;
import com.drivique.api.dto.PromotionValidationResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.AdditionalService;
import com.drivique.api.model.InsuranceCoverage;
import com.drivique.api.model.MileagePlan;
import com.drivique.api.model.Vehicle;
import com.drivique.api.model.VehicleCategory;
import com.drivique.api.repository.AdditionalServiceRepository;
import com.drivique.api.repository.InsuranceCoverageRepository;
import com.drivique.api.repository.MileagePlanRepository;
import com.drivique.api.repository.VehicleRepository;
import com.drivique.api.service.PricingEngineService;
import com.drivique.api.service.PromotionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingQuoteCalculationTests {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private InsuranceCoverageRepository insuranceCoverageRepository;

    @Mock
    private MileagePlanRepository mileagePlanRepository;

    @Mock
    private AdditionalServiceRepository additionalServiceRepository;

    @Mock
    private PromotionValidationService promotionValidationService;

    private PricingEngineService pricingEngineService;

    private UUID vehicleId;
    private UUID insuranceId;
    private UUID mileagePlanId;
    private UUID extraServiceId;

    private Vehicle vehicleMock;
    private VehicleCategory categoryMock;
    private InsuranceCoverage coverageMock;
    private MileagePlan planMock;
    private AdditionalService extraMock;

    @BeforeEach
    void setUp() {
        pricingEngineService = new PricingEngineService(
                vehicleRepository,
                insuranceCoverageRepository,
                mileagePlanRepository,
                additionalServiceRepository,
                promotionValidationService
        );

        vehicleId = UUID.randomUUID();
        insuranceId = UUID.randomUUID();
        mileagePlanId = UUID.randomUUID();
        extraServiceId = UUID.randomUUID();

        categoryMock = mock(VehicleCategory.class);
        when(categoryMock.getId()).thenReturn(UUID.randomUUID());
        when(categoryMock.getSecurityDeposit()).thenReturn(new BigDecimal("500000.00"));

        vehicleMock = mock(Vehicle.class);
        when(vehicleMock.getId()).thenReturn(vehicleId);
        when(vehicleMock.getDailyRate()).thenReturn(new BigDecimal("100000.00"));
        when(vehicleMock.getCategory()).thenReturn(categoryMock);

        coverageMock = mock(InsuranceCoverage.class);
        when(coverageMock.isActive()).thenReturn(true);
        when(coverageMock.getDailyRate()).thenReturn(new BigDecimal("20000.00"));

        planMock = mock(MileagePlan.class);
        when(planMock.isActive()).thenReturn(true);
        when(planMock.getDailyRate()).thenReturn(new BigDecimal("10000.00"));

        extraMock = mock(AdditionalService.class);
        when(extraMock.isActive()).thenReturn(true);
        when(extraMock.getDailyRate()).thenReturn(new BigDecimal("5000.00"));
    }

    @Test
    void quoteCalculatesCompleteBreakdownAccurately() {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicleMock));
        when(insuranceCoverageRepository.findById(insuranceId)).thenReturn(Optional.of(coverageMock));
        when(mileagePlanRepository.findById(mileagePlanId)).thenReturn(Optional.of(planMock));
        when(additionalServiceRepository.findById(extraServiceId)).thenReturn(Optional.of(extraMock));

        LocalDate pickup = LocalDate.of(2026, 11, 1);
        LocalDate returnDate = LocalDate.of(2026, 11, 4); // 3 days

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(extraServiceId, extraServiceId), // Test distinct extras handling
                null
        );

        PriceQuoteResponseDTO quote = pricingEngineService.quote(request, "tester@drivique.com");

        assertThat(quote).isNotNull();
        assertThat(quote.vehicleId()).isEqualTo(vehicleId);
        assertThat(quote.rentalDays()).isEqualTo(3);
        assertThat(quote.vehicleSubtotal()).isEqualByComparingTo("300000.00");
        assertThat(quote.insuranceSubtotal()).isEqualByComparingTo("60000.00");
        assertThat(quote.mileagePlanSubtotal()).isEqualByComparingTo("30000.00");
        assertThat(quote.additionalServicesSubtotal()).isEqualByComparingTo("15000.00");
        assertThat(quote.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.refundableSecurityDeposit()).isEqualByComparingTo("500000.00");
        assertThat(quote.estimatedTotal()).isEqualByComparingTo("405000.00");
        assertThat(quote.couponCode()).isNull();
    }

    @Test
    void quoteCalculatesSingleDayWhenSamePickupAndReturnDate() {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicleMock));
        when(insuranceCoverageRepository.findById(insuranceId)).thenReturn(Optional.of(coverageMock));
        when(mileagePlanRepository.findById(mileagePlanId)).thenReturn(Optional.of(planMock));

        LocalDate sameDay = LocalDate.of(2026, 11, 1);

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                sameDay,
                sameDay,
                insuranceId,
                mileagePlanId,
                null,
                "   " // Blank coupon handling
        );

        PriceQuoteResponseDTO quote = pricingEngineService.quote(request, "tester@drivique.com");

        assertThat(quote).isNotNull();
        assertThat(quote.rentalDays()).isEqualTo(1);
        assertThat(quote.vehicleSubtotal()).isEqualByComparingTo("100000.00");
        assertThat(quote.additionalServicesSubtotal()).isEqualByComparingTo("0.00");
        assertThat(quote.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(quote.estimatedTotal()).isEqualByComparingTo("130000.00");
    }

    @Test
    void quoteAppliesPromotionDiscountWhenCouponCodeIsValid() {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicleMock));
        when(insuranceCoverageRepository.findById(insuranceId)).thenReturn(Optional.of(coverageMock));
        when(mileagePlanRepository.findById(mileagePlanId)).thenReturn(Optional.of(planMock));
        when(additionalServiceRepository.findById(extraServiceId)).thenReturn(Optional.of(extraMock));

        PromotionValidationResponseDTO promoResponse = new PromotionValidationResponseDTO(
                UUID.randomUUID(),
                "DESCUENTO10",
                "PERCENTAGE",
                new BigDecimal("10.00"),
                new BigDecimal("40500.00"),
                new BigDecimal("364500.00")
        );

        when(promotionValidationService.validate(any(PromotionValidationRequestDTO.class), eq("tester@drivique.com")))
                .thenReturn(promoResponse);

        LocalDate pickup = LocalDate.of(2026, 11, 1);
        LocalDate returnDate = LocalDate.of(2026, 11, 4);

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(extraServiceId),
                "  DESCUENTO10  "
        );

        PriceQuoteResponseDTO quote = pricingEngineService.quote(request, "tester@drivique.com");

        assertThat(quote).isNotNull();
        assertThat(quote.couponCode()).isEqualTo("DESCUENTO10");
        assertThat(quote.discountAmount()).isEqualByComparingTo("40500.00");
        assertThat(quote.estimatedTotal()).isEqualByComparingTo("364500.00");
    }

    @Test
    void quoteRejectsWhenReturnDateIsBeforePickupDate() {
        LocalDate pickup = LocalDate.of(2026, 11, 4);
        LocalDate returnDate = LocalDate.of(2026, 11, 1);

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(extraServiceId),
                null
        );

        assertThatThrownBy(() -> pricingEngineService.quote(request, "tester@drivique.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Return date cannot precede pickup date");
    }

    @Test
    void quoteThrowsWhenVehicleIsNotFound() {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.empty());

        LocalDate pickup = LocalDate.of(2026, 11, 1);
        LocalDate returnDate = LocalDate.of(2026, 11, 3);

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(),
                null
        );

        assertThatThrownBy(() -> pricingEngineService.quote(request, "tester@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Active vehicle not found");
    }

    @Test
    void quoteThrowsWhenInsuranceCoverageNotFoundOrInactive() {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicleMock));
        when(insuranceCoverageRepository.findById(insuranceId)).thenReturn(Optional.empty());

        LocalDate pickup = LocalDate.of(2026, 11, 1);
        LocalDate returnDate = LocalDate.of(2026, 11, 3);

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(),
                null
        );

        assertThatThrownBy(() -> pricingEngineService.quote(request, "tester@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Active insurance coverage not found");
    }

    @Test
    void quoteThrowsWhenMileagePlanNotFoundOrInactive() {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicleMock));
        when(insuranceCoverageRepository.findById(insuranceId)).thenReturn(Optional.of(coverageMock));
        when(mileagePlanRepository.findById(mileagePlanId)).thenReturn(Optional.empty());

        LocalDate pickup = LocalDate.of(2026, 11, 1);
        LocalDate returnDate = LocalDate.of(2026, 11, 3);

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(),
                null
        );

        assertThatThrownBy(() -> pricingEngineService.quote(request, "tester@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Active mileage plan not found");
    }

    @Test
    void quoteThrowsWhenAdditionalServiceNotFoundOrInactive() {
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicleMock));
        when(insuranceCoverageRepository.findById(insuranceId)).thenReturn(Optional.of(coverageMock));
        when(mileagePlanRepository.findById(mileagePlanId)).thenReturn(Optional.of(planMock));
        when(additionalServiceRepository.findById(extraServiceId)).thenReturn(Optional.empty());

        LocalDate pickup = LocalDate.of(2026, 11, 1);
        LocalDate returnDate = LocalDate.of(2026, 11, 3);

        PriceQuoteRequestDTO request = new PriceQuoteRequestDTO(
                vehicleId,
                pickup,
                returnDate,
                insuranceId,
                mileagePlanId,
                List.of(extraServiceId),
                null
        );

        assertThatThrownBy(() -> pricingEngineService.quote(request, "tester@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Active additional service not found");
    }
}
