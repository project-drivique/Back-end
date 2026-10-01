package com.drivique.api.specification;

import com.drivique.api.model.Vehicle;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VehicleSpecification {

    private VehicleSpecification() {}

    public static Specification<Vehicle> withDynamicFilters(
            UUID branchId,
            UUID categoryId,
            UUID transmissionId,
            UUID fuelId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean featured
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only active vehicles
            predicates.add(cb.isTrue(root.get("active")));

            // Only reservable and active status
            predicates.add(cb.isTrue(root.get("status").get("allowsReservation")));
            predicates.add(cb.isTrue(root.get("status").get("active")));

            // Filter by current branch
            if (branchId != null) {
                predicates.add(cb.equal(root.get("currentBranch").get("id"), branchId));
            }

            // Filter by category
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            // Filter by transmission
            if (transmissionId != null) {
                predicates.add(cb.equal(root.get("transmissionType").get("id"), transmissionId));
            }

            // Filter by fuel
            if (fuelId != null) {
                predicates.add(cb.equal(root.get("fuelType").get("id"), fuelId));
            }

            // Filter by min price
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dailyRate"), minPrice));
            }

            // Filter by max price
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dailyRate"), maxPrice));
            }

            // Filter by featured flag
            if (featured != null) {
                predicates.add(cb.equal(root.get("featured"), featured));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
