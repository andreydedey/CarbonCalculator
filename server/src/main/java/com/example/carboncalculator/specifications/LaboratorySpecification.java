package com.example.carboncalculator.specifications;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.Laboratory;

public final class LaboratorySpecification {

    private LaboratorySpecification() {}

    public static Specification<Laboratory> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }}
