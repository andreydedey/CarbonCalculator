package com.example.carboncalculator.exceptions;

import java.time.YearMonth;

public class DuplicateEmissionFactorException extends RuntimeException {
    public DuplicateEmissionFactorException(YearMonth referenceMonth) {
        super("Já existe um fator de emissão para " + referenceMonth + ".");
    }
}
