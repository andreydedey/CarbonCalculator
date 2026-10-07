package com.example.carboncalculator.repositories;

import java.time.LocalDate;
import java.util.List;

import com.example.carboncalculator.dto.SnapshotBucketDTO;
import com.example.carboncalculator.entities.SnapshotGranularity;

public interface EmissionSnapshotRepositoryCustom {

    List<SnapshotBucketDTO> findBucketsMostRecentFirst(
            SnapshotGranularity granularity, LocalDate startDate, LocalDate endDate, long offset, int limit);

    List<SnapshotBucketDTO> findAllBucketsMostRecentFirst(
            SnapshotGranularity granularity, LocalDate startDate, LocalDate endDate);

    long countBuckets(SnapshotGranularity granularity, LocalDate startDate, LocalDate endDate);
}
