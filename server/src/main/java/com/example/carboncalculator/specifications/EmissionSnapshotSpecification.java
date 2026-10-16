package com.example.carboncalculator.specifications;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.EmissionSnapshot;

public final class EmissionSnapshotSpecification {

    private EmissionSnapshotSpecification() {}

    public static Specification<EmissionSnapshot> ofPeriod(UUID periodId) {
        return (root, query, cb) -> cb.equal(root.get("academicPeriod").get("id"), periodId);
    }

    public static Specification<EmissionSnapshot> withinDateRange(LocalDate start, LocalDate end) {
        return (root, query, cb) -> cb.between(root.get("snapshotDate"), start, end);
    }

    public static Specification<EmissionSnapshot> ofLaboratory(UUID laboratoryId) {
        return (root, query, cb) -> cb.equal(root.get("laboratory").get("id"), laboratoryId);
    }
}
