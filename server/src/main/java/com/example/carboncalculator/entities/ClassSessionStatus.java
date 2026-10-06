package com.example.carboncalculator.entities;

/** How a class on a given date relates to the weekly grid. */
public enum ClassSessionStatus {
    /** Follows the weekly grid. */
    GRID,
    /** In the grid, but with a different number of stations on this date. */
    ADJUSTED,
    /** In the grid, but did not happen on this date. */
    CANCELLED,
    /** Not in the grid; happened only on this date. */
    EXTRA;

    public static ClassSessionStatus of(Integer gridStations, int stationsUsed) {
        if (stationsUsed == 0) return CANCELLED;
        if (gridStations == null) return EXTRA;
        return gridStations == stationsUsed ? GRID : ADJUSTED;
    }
}
