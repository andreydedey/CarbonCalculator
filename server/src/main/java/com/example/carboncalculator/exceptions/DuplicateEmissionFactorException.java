package com.example.carboncalculator.exceptions;

public class DuplicateEmissionFactorException extends RuntimeException {
    public DuplicateEmissionFactorException(short year, short month) {
        super("Já existe um fator de emissão para " + String.format("%02d/%d", month, year) + ".");
    }
}
