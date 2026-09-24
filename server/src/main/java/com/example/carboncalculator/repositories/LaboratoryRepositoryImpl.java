package com.example.carboncalculator.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.example.carboncalculator.dto.LaboratoryDTO;
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
public class LaboratoryRepositoryImpl implements LaboratoryRepositoryCustom {

    private final EntityManager em;

    @Override
    public Page<LaboratoryDTO> findAllWithCounts(Specification<Laboratory> spec, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<LaboratoryDTO> cq = cb.createQuery(LaboratoryDTO.class);
        Root<Laboratory> root = cq.from(Laboratory.class);

        // Configuration count (distinct configurations linked)
        Subquery<Long> configCountSq = cq.subquery(Long.class);
        Root<LaboratoryEquipment> leConfig = configCountSq.from(LaboratoryEquipment.class);
        configCountSq.select(cb.count(leConfig));
        configCountSq.where(cb.equal(leConfig.get("laboratory"), root));

        // Total stations (sum of quantities)
        Subquery<Long> totalStationsSq = cq.subquery(Long.class);
        Root<LaboratoryEquipment> leStations = totalStationsSq.from(LaboratoryEquipment.class);
        totalStationsSq.select(cb.coalesce(cb.sum(leStations.get("quantity").as(Long.class)), 0L));
        totalStationsSq.where(cb.equal(leStations.get("laboratory"), root));

        cq.select(cb.construct(
                LaboratoryDTO.class,
                root.get("id"),
                root.get("name"),
                root.get("description"),
                root.get("active"),
                configCountSq.getSelection(),
                totalStationsSq.getSelection(),
                root.get("createdAt")));

        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }

        cq.orderBy(cb.asc(root.get("name")));

        TypedQuery<LaboratoryDTO> query = em.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<LaboratoryDTO> content = query.getResultList();

        // Count query
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<Laboratory> countRoot = countCq.from(Laboratory.class);
        countCq.select(cb.count(countRoot));

        Predicate countPredicate = spec.toPredicate(countRoot, countCq, cb);
        if (countPredicate != null) {
            countCq.where(countPredicate);
        }

        Long total = em.createQuery(countCq).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }
}
