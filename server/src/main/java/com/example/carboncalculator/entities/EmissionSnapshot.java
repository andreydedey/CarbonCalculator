package com.example.carboncalculator.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable daily emission snapshot, captured automatically by the cron job
 * for each school day within an active academic period (US-034). The query
 * API aggregates these records by granularity (US-033).
 */
@Entity
@Table(name = "emission_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissionSnapshot {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private AcademicPeriod academicPeriod;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "is_school_day", nullable = false)
    private boolean schoolDay;

    @Column(name = "daily_emission_kg", nullable = false, precision = 12, scale = 4)
    private BigDecimal dailyEmissionKg;

    @Column(name = "daily_energy_kwh", nullable = false, precision = 12, scale = 4)
    private BigDecimal dailyEnergyKwh;

    @Column(name = "emission_factor_value", nullable = false, precision = 10, scale = 6)
    private BigDecimal emissionFactorValue;

    @Column(name = "station_count", nullable = false)
    private int stationCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }
}
