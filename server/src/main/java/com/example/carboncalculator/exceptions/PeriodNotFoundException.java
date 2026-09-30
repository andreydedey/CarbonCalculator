package com.example.carboncalculator.exceptions;

import java.util.UUID;

public class PeriodNotFoundException extends RuntimeException {
    public PeriodNotFoundException(UUID id) {
        super("Período letivo não encontrado: " + id);
    }
}
