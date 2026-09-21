package com.example.carboncalculator.specifications;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.MembershipStatus;
import com.example.carboncalculator.entities.UserInstitution;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class InstitutionSpecification {

    private InstitutionSpecification() {}

    public static Specification<Institution> hasActiveMember(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> memberships = query.subquery(UUID.class);
            Root<UserInstitution> membership = memberships.from(UserInstitution.class);
            memberships.select(membership.get("institution").get("id"))
                    .where(
                            cb.equal(membership.get("user").get("id"), userId),
                            cb.equal(membership.get("status"), MembershipStatus.ACTIVE));
            return root.get("id").in(memberships);
        };
    }
}
