package com.example.carboncalculator.exceptions;

import java.time.LocalDate;

public class HolidayOutOfRangeException extends RuntimeException {
    public HolidayOutOfRangeException(LocalDate date, LocalDate start, LocalDate end) {
        super("Feriado " + date + " está fora do intervalo do período [" + start + ", " + end + "]");
    }
}
