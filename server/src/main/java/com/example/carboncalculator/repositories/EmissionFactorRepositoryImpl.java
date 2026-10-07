package com.example.carboncalculator.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.example.carboncalculator.dto.EmissionFactorDTO;
import com.example.carboncalculator.entities.EmissionFactor;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EmissionFactorRepositoryImpl implements EmissionFactorRepositoryCustom {

    private final EntityManager em;

    @Override
    public Page<EmissionFactorDTO> findAllProjected(Specification<EmissionFactor> spec, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<EmissionFactorDTO> cq = cb.createQuery(EmissionFactorDTO.class);
        Root<EmissionFactor> root = cq.from(EmissionFactor.class);

        cq.select(cb.construct(
                EmissionFactorDTO.class,
                root.get("id"),
                root.get("referenceMonth"),
                root.get("value"),
                root.get("source")));

        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }

        cq.orderBy(cb.desc(root.get("referenceMonth")));

        TypedQuery<EmissionFactorDTO> query = em.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<EmissionFactorDTO> content = query.getResultList();

        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<EmissionFactor> countRoot = countCq.from(EmissionFactor.class);
        countCq.select(cb.count(countRoot));

        Predicate countPredicate = spec.toPredicate(countRoot, countCq, cb);
        if (countPredicate != null) {
            countCq.where(countPredicate);
        }

        Long total = em.createQuery(countCq).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }
}
