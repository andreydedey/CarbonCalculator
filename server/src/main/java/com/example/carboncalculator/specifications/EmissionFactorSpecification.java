package com.example.carboncalculator.specifications;

import java.time.YearMonth;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.EmissionFactor;

public final class EmissionFactorSpecification {

    private EmissionFactorSpecification() {}

    public static Specification<EmissionFactor> referenceMonthInYear(Integer year) {
        if (year == null) return (root, query, cb) -> cb.conjunction();
        YearMonth start = YearMonth.of(year, 1);
        YearMonth end = YearMonth.of(year, 12);
        return (root, query, cb) -> cb.between(root.get("referenceMonth"), start, end);
    }
}
