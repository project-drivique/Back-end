package com.drivique.api.service;

import com.drivique.api.dto.CreateReservationRequestDTO;
import com.drivique.api.dto.DeliveryPointResponseDTO;
import com.drivique.api.dto.ReservationAdditionalServiceItemDTO;
import com.drivique.api.dto.ReservationPriceBreakdownDTO;
import com.drivique.api.dto.ReservationPromotionItemDTO;
import com.drivique.api.dto.ReservationResponseDTO;
import com.drivique.api.model.Branch;
import com.drivique.api.model.InsuranceCoverage;
import com.drivique.api.model.MileagePlan;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.User;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.ReservationAdditionalServiceRepository;
import com.drivique.api.repository.ReservationPromotionRepository;
import com.drivique.api.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ReservationResponseMapper {

    private ReservationResponseMapper() {
    }

    static Reservation findIdenticalOpenRequest(
            ReservationRepository reservationRepository,
            ReservationAdditionalServiceRepository additionalServiceRepository,
            ReservationPromotionRepository promotionRepository,
            User customer,
            Vehicle vehicle,
            CreateReservationRequestDTO request
    ) {
        for (Reservation open : reservationRepository.findOpenRequestsForSameTrip(
                customer.getId(), vehicle.getId(), request.pickupDate(), request.returnDate())) {
            if (!Objects.equals(idOf(open.getInsuranceCoverage()), request.insuranceCoverageId()) && request.insuranceCoverageId() != null) {
                continue;
            }
            if (!Objects.equals(idOf(open.getMileagePlan()), request.mileagePlanId()) && request.mileagePlanId() != null) {
                continue;
            }
            if (!Objects.equals(idOf(open.getCashPaymentBranch()), request.cashPaymentBranchId())) {
                continue;
            }
            Set<UUID> wantedServices = request.additionalServiceIds() == null ? Set.of()
                    : request.additionalServiceIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
            Set<UUID> savedServices = additionalServiceRepository.findByReservationId(open.getId()).stream()
                    .map(ras -> ras.getAdditionalService().getId()).collect(Collectors.toSet());
            if (!wantedServices.equals(savedServices)) {
                continue;
            }
            String wantedCoupon = request.couponCode() == null ? "" : request.couponCode().strip();
            String savedCoupon = promotionRepository.findByReservationId(open.getId()).stream()
                    .map(rp -> rp.getPromotion().getCode()).findFirst().orElse("");
            if (!wantedCoupon.equalsIgnoreCase(savedCoupon)) {
                continue;
            }
            return open;
        }
        return null;
    }

    private static UUID idOf(Object entity) {
        if (entity instanceof InsuranceCoverage i) return i.getId();
        if (entity instanceof MileagePlan m) return m.getId();
        if (entity instanceof Branch b) return b.getId();
        return null;
    }

    static boolean isOutsideBranchSchedule(Branch pickupBranch) {
        if (pickupBranch == null || pickupBranch.getOpeningTime() == null || pickupBranch.getClosingTime() == null) {
            return false;
        }
        LocalTime open = pickupBranch.getOpeningTime();
        LocalTime close = pickupBranch.getClosingTime();
        boolean is24Hours = (open.equals(LocalTime.MIN)
                && (close.equals(LocalTime.MAX) || (close.getHour() == 23 && close.getMinute() >= 59)))
                || open.equals(close);
        if (is24Hours) {
            return false;
        }
        LocalTime nowTime = LocalTime.now(ZoneId.of("America/Bogota"));
        if (open.isBefore(close)) {
            return nowTime.isBefore(open) || nowTime.isAfter(close);
        }
        return nowTime.isBefore(open) && nowTime.isAfter(close);
    }

    static List<String> allowedTransitionsFor(String currentStatusCode) {
        List<String> allowed = new ArrayList<>();
        switch (currentStatusCode) {
            case "PENDING_SCHEDULE" -> {
                allowed.add("PENDING_PAYMENT");
                allowed.add("PENDING_DOCUMENT_REVIEW");
                allowed.add("CANCELLED_BY_USER");
            }
            case "PENDING_DOCUMENT_REVIEW" -> {
                allowed.add("PENDING_PAYMENT");
                allowed.add("CANCELLED_BY_USER");
            }
            case "PENDING_PAYMENT" -> {
                allowed.add("PENDING_SIGNATURE");
                allowed.add("CONFIRMED");
                allowed.add("CANCELLED_BY_USER");
                allowed.add("CANCELLED_BY_TIMEOUT");
            }
            case "PENDING_SIGNATURE" -> {
                allowed.add("CONFIRMED");
                allowed.add("CANCELLED_BY_USER");
            }
            case "CONFIRMED" -> {
                allowed.add("ACTIVE");
                allowed.add("CANCELLED_BY_USER");
            }
            case "ACTIVE" -> allowed.add("COMPLETED");
            default -> {
            }
        }
        return allowed;
    }

    static ReservationResponseDTO mapFromEntity(Reservation r, ReservationPricing reservationPricing) {
        long seconds = Duration.between(r.getPickupDate(), r.getReturnDate()).toSeconds();
        int rentalDays = (int) Math.max(1, (seconds + 86399) / 86400);
        BigDecimal vehicleSubtotal = r.getDailyRate().multiply(BigDecimal.valueOf(rentalDays));

        List<ReservationAdditionalServiceItemDTO> services = r.getAdditionalServices().stream()
                .map(s -> new ReservationAdditionalServiceItemDTO(
                        s.getAdditionalService().getId(),
                        s.getAdditionalService().getName(),
                        s.getQuantity(),
                        s.getDailyRate(),
                        s.getDailyRate().multiply(BigDecimal.valueOf(rentalDays)).multiply(BigDecimal.valueOf(s.getQuantity()))
                ))
                .toList();

        ReservationPromotionItemDTO promo = r.getPromotions().stream().findFirst()
                .map(p -> new ReservationPromotionItemDTO(
                        p.getPromotion().getId(),
                        p.getPromotion().getCode(),
                        p.getDiscountApplied()
                ))
                .orElse(null);

        return mapToDTO(r, rentalDays, vehicleSubtotal, services, promo, reservationPricing);
    }

    static ReservationResponseDTO mapToDTO(
            Reservation r,
            int rentalDays,
            BigDecimal vehicleSubtotal,
            List<ReservationAdditionalServiceItemDTO> services,
            ReservationPromotionItemDTO promo,
            ReservationPricing reservationPricing
    ) {
        List<DeliveryPointResponseDTO> deliveryPointDTOs = r.getDeliveryPoints() != null
                ? r.getDeliveryPoints().stream()
                .map(pt -> DeliveryPointResponseDTO.from(pt, isOwnerRequest(r)))
                .toList()
                : List.of();

        BigDecimal days = BigDecimal.valueOf(rentalDays);
        BigDecimal insuranceSubtotal = r.getInsuranceCoverage().getDailyRate().multiply(days);
        BigDecimal mileageSubtotal = r.getMileagePlan().getDailyRate().multiply(days);
        BigDecimal servicesSubtotal = services.stream().map(ReservationAdditionalServiceItemDTO::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal baseSubtotal = vehicleSubtotal.add(insuranceSubtotal).add(mileageSubtotal).add(servicesSubtotal);
        BigDecimal discount = promo == null || promo.discountApplied() == null ? BigDecimal.ZERO : promo.discountApplied();
        ReservationPricing.Breakdown fees = reservationPricing.calculate(baseSubtotal, discount);
        boolean includesFees = fees.total().compareTo(r.getTotalEstimated()) == 0;
        ReservationPriceBreakdownDTO breakdown = new ReservationPriceBreakdownDTO(
                insuranceSubtotal,
                mileageSubtotal,
                servicesSubtotal,
                baseSubtotal,
                discount,
                includesFees ? fees.adminFee() : null,
                includesFees ? reservationPricing.adminFeeRate() : null,
                includesFees ? fees.vat() : null,
                includesFees ? reservationPricing.vatRate() : null
        );

        return new ReservationResponseDTO(
                r.getId(),
                r.getCode(),
                r.getCustomer().getId(),
                r.getCustomer().getFirstName() + " " + r.getCustomer().getLastName(),
                r.getCustomer().getEmail(),
                r.getVehicle().getId(),
                r.getVehicle().getModel(),
                r.getVehicle().getPlate(),
                r.getStatus().getCode(),
                r.getStatus().getName(),
                r.getPickupDate(),
                r.getReturnDate(),
                rentalDays,
                r.getDailyRate(),
                vehicleSubtotal,
                r.getInsuranceCoverage().getId(),
                r.getInsuranceCoverage().getName(),
                r.getInsuranceCoverage().getDailyRate(),
                r.getMileagePlan().getId(),
                r.getMileagePlan().getName(),
                r.getMileagePlan().getDailyRate(),
                services,
                promo,
                r.getTotalEstimated(),
                r.getCashPaymentBranch() != null ? r.getCashPaymentBranch().getId() : null,
                r.getCashPaymentCode(),
                r.getCashPaymentExpiresAt(),
                r.isBlocksAvailability(),
                deliveryPointDTOs,
                r.getCreatedAt(),
                breakdown
        );
    }

    /** Estados a los que una reserva solo llega después de un pago aprobado (en línea o en efectivo). */
    private static final Set<String> PAID_STATUSES = Set.of(
            "PENDING_SIGNATURE", "CONFIRMED", "IN_PROGRESS", "ACTIVE", "COMPLETED");

    /** El cliente solo recibe el PIN de entrega en un estado posterior al pago; cualquier otro estado lo oculta. */
    static boolean customerMaySeeDeliveryPin(String statusCode) {
        return statusCode != null && PAID_STATUSES.contains(statusCode.toUpperCase());
    }

    private static boolean isOwnerRequest(Reservation r) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return false;
        }
        boolean staff = auth.getAuthorities().stream().anyMatch(a -> {
            String role = a.getAuthority();
            return role.contains("ADMIN") || role.contains("EMPLOYEE") || role.contains("BRANCH") || role.contains("rental");
        });
        if (staff) {
            return true;
        }
        boolean owner = r.getCustomer() != null && auth.getName().equalsIgnoreCase(r.getCustomer().getEmail());
        return owner && r.getStatus() != null && customerMaySeeDeliveryPin(r.getStatus().getCode());
    }
}
