package com.example.carboncalculator.exceptions;

public class GpuTdpRequiredException extends RuntimeException {
    public GpuTdpRequiredException() {
        super("A potência de projeto (TDP) da GPU é obrigatória quando o modelo da GPU é informado");
    }
}
