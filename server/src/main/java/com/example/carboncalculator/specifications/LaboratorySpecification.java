package com.example.carboncalculator.specifications;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.Laboratory;

public final class LaboratorySpecification {

    private LaboratorySpecification() {}

    public static Specification<Laboratory> hasActive(boolean active) {
        return (root, query, cb) -> active
                ? cb.isTrue(root.get("active"))
                : cb.isFalse(root.get("active"));
    }

    public static Specification<Laboratory> nameContains(String name) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
}
