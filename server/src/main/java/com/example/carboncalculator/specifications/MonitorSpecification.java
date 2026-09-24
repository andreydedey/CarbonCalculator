package com.example.carboncalculator.specifications;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.Monitor;

public final class MonitorSpecification {

    private MonitorSpecification() {}

    public static Specification<Monitor> nameContains(String name) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
}
