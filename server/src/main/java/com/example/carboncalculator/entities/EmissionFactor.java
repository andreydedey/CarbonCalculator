package com.example.carboncalculator.entities;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.example.carboncalculator.converters.YearMonthAttributeConverter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "emission_factor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissionFactor {

    @Id
    @GeneratedValue
    private UUID id;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "reference_month", nullable = false)
    private YearMonth referenceMonth;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal value;

    @Column(nullable = false, length = 500)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;
}
