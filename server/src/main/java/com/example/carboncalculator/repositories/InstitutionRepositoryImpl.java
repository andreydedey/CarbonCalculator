package com.example.carboncalculator.repositories;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.entities.Institution;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InstitutionRepositoryImpl implements InstitutionRepositoryCustom {

    private final EntityManager em;

    @Override
    public Page<InstitutionDTO> findAllWithCounts(Specification<Institution> spec, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Get filtered institution IDs first (respects specification + pagination)
        CriteriaQuery<Institution> cq = cb.createQuery(Institution.class);
        Root<Institution> root = cq.from(Institution.class);

        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        cq.orderBy(cb.asc(root.get("name")));

        TypedQuery<Institution> query = em.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Institution> institutions = query.getResultList();

        // Use SECURITY DEFINER functions to count across RLS boundaries
        List<InstitutionDTO> content = institutions.stream()
                .map(inst -> {
                    long labCount = ((Number) em.createNativeQuery(
                            "SELECT count_labs_for_institution(:id)")
                            .setParameter("id", inst.getId())
                            .getSingleResult()).longValue();

                    long equipCount = ((Number) em.createNativeQuery(
                            "SELECT sum_equipment_for_institution(:id)")
                            .setParameter("id", inst.getId())
                            .getSingleResult()).longValue();

                    return new InstitutionDTO(
                            inst.getId(),
                            inst.getName(),
                            inst.getAcronym(),
                            inst.getCity(),
                            inst.getState(),
                            inst.isActive(),
                            labCount,
                            equipCount,
                            inst.getCreatedAt());
                })
                .toList();

        // Count query
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<Institution> countRoot = countCq.from(Institution.class);
        countCq.select(cb.count(countRoot));

        Predicate countPredicate = spec.toPredicate(countRoot, countCq, cb);
        if (countPredicate != null) {
            countCq.where(countPredicate);
        }

        Long total = em.createQuery(countCq).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }
}
