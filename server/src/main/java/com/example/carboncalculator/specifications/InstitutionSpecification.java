package com.example.carboncalculator.specifications;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.Institution;

public final class InstitutionSpecification {

    private InstitutionSpecification() {}

    public static Specification<Institution> hasIdIn(List<UUID> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }
}
