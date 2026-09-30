package com.example.carboncalculator.exceptions;

public class PeriodOverlapException extends RuntimeException {
    public PeriodOverlapException() {
        super("Já existe um período letivo com datas sobrepostas nesta instituição");
    }
}
