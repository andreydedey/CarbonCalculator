package com.example.carboncalculator.exceptions;

public class ScheduleBlockOverlapException extends RuntimeException {
    public ScheduleBlockOverlapException(int dayOfWeek) {
        super("Blocos de horário sobrepostos no dia " + dayOfWeek);
    }
}
