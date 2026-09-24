package com.example.carboncalculator.specifications;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.EquipmentModel;

public final class EquipmentModelSpecification {

    private EquipmentModelSpecification() {}

    public static Specification<EquipmentModel> nameContains(String name) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
}
