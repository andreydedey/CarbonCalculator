package com.example.carboncalculator.entities;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-date exception to the weekly grid for one class (shift + slot) of a laboratory.
 * {@code stationsUsed = 0} cancels the class; a slot that is free in the grid is an extra class.
 */
@Entity
@Table(name = "class_occurrence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassOccurrence {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private AcademicPeriodShift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private short slot;

    @Column(name = "stations_used", nullable = false)
    private short stationsUsed;
}
