package com.example.carboncalculator.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.example.carboncalculator.dto.InstitutionDTO;
import com.example.carboncalculator.entities.Institution;
import com.example.carboncalculator.entities.Laboratory;
import com.example.carboncalculator.entities.LaboratoryEquipment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InstitutionRepositoryImpl implements InstitutionRepositoryCustom {

    private final EntityManager em;

    @Override
    public Page<InstitutionDTO> findAllWithCounts(Specification<Institution> spec, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Main query
        CriteriaQuery<InstitutionDTO> cq = cb.createQuery(InstitutionDTO.class);
        Root<Institution> root = cq.from(Institution.class);

        // Lab count subquery
        Subquery<Long> labCountSq = cq.subquery(Long.class);
        Root<Laboratory> labRoot = labCountSq.from(Laboratory.class);
        labCountSq.select(cb.count(labRoot));
        labCountSq.where(cb.equal(labRoot.get("institution"), root));

        // Equipment count subquery
        Subquery<Long> equipCountSq = cq.subquery(Long.class);
        Root<LaboratoryEquipment> leRoot = equipCountSq.from(LaboratoryEquipment.class);
        equipCountSq.select(cb.coalesce(cb.sum(leRoot.get("quantity").as(Long.class)), 0L));
        equipCountSq.where(cb.equal(leRoot.get("laboratory").get("institution"), root));

        cq.select(cb.construct(
                InstitutionDTO.class,
                root.get("id"),
                root.get("name"),
                root.get("acronym"),
                root.get("city"),
                root.get("state"),
                root.get("active"),
                labCountSq,
                equipCountSq,
                root.get("createdAt")));

        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }

        cq.orderBy(cb.asc(root.get("name")));

        TypedQuery<InstitutionDTO> query = em.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<InstitutionDTO> content = query.getResultList();

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
