package com.example.carboncalculator.specifications;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.EmissionSnapshot;

public final class EmissionSnapshotSpecification {

    private EmissionSnapshotSpecification() {}

    public static Specification<EmissionSnapshot> withinDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> cb.between(root.get("snapshotDate"), start, end);
    }
}
