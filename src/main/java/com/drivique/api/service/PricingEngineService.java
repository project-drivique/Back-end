package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingEngineService {
    private final VehicleRepository vehicles; private final InsuranceCoverageRepository coverages;
    private final MileagePlanRepository mileagePlans; private final AdditionalServiceRepository additionalServices;
    private final PromotionValidationService promotions;
    public PricingEngineService(VehicleRepository vehicles, InsuranceCoverageRepository coverages,
            MileagePlanRepository mileagePlans, AdditionalServiceRepository additionalServices, PromotionValidationService promotions) {
        this.vehicles=vehicles; this.coverages=coverages; this.mileagePlans=mileagePlans; this.additionalServices=additionalServices; this.promotions=promotions;
    }
    @Transactional(readOnly=true)
    public PriceQuoteResponseDTO quote(PriceQuoteRequestDTO input, String email) {
        if(input.returnDate().isBefore(input.pickupDate())) throw new IllegalArgumentException("Return date cannot precede pickup date");
        int days=(int)Math.max(1, ChronoUnit.DAYS.between(input.pickupDate(),input.returnDate()));
        Vehicle vehicle=vehicles.findByIdAndActiveTrue(input.vehicleId()).orElseThrow(()->new ResourceNotFoundException("Active vehicle not found"));
        InsuranceCoverage coverage=coverages.findById(input.insuranceId()).filter(InsuranceCoverage::isActive).orElseThrow(()->new ResourceNotFoundException("Active insurance coverage not found"));
        MileagePlan plan=mileagePlans.findById(input.mileagePlanId()).filter(MileagePlan::isActive).orElseThrow(()->new ResourceNotFoundException("Active mileage plan not found"));
        BigDecimal multiplier=BigDecimal.valueOf(days);
        BigDecimal vehicleSubtotal=vehicle.getDailyRate().multiply(multiplier), insuranceSubtotal=coverage.getDailyRate().multiply(multiplier), mileageSubtotal=plan.getDailyRate().multiply(multiplier);
        BigDecimal extras=Optional.ofNullable(input.additionalServiceIds()).orElse(List.of()).stream().distinct()
                .map(id->additionalServices.findById(id).filter(AdditionalService::isActive).orElseThrow(()->new ResourceNotFoundException("Active additional service not found")))
                .map(s->s.getDailyRate().multiply(multiplier)).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal discount=BigDecimal.ZERO; String coupon=null;
        if(input.couponCode()!=null&&!input.couponCode().isBlank()) { coupon=input.couponCode().strip(); discount=promotions.validate(new PromotionValidationRequestDTO(coupon,vehicle.getId(),vehicle.getCategory().getId(),input.pickupDate(),input.returnDate()),email).discountAmount(); }
        BigDecimal total=vehicleSubtotal.add(insuranceSubtotal).add(mileageSubtotal).add(extras).subtract(discount);
        return new PriceQuoteResponseDTO(vehicle.getId(),days,vehicleSubtotal,insuranceSubtotal,mileageSubtotal,extras,discount,vehicle.getCategory().getSecurityDeposit(),total,coupon);
    }
}
