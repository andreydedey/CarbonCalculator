import { api } from './client.ts'
import type { ShiftType } from './academic-periods'

// --- Types ---

export type ClassSessionStatus = 'GRID' | 'ADJUSTED' | 'CANCELLED' | 'EXTRA'

export type ClassOccurrence = {
  id: string
  laboratoryId: string
  laboratoryName: string
  shiftId: string
  shiftType: ShiftType
  date: string
  slot: number
  // null for an extra class (slot free in the weekly grid)
  gridStations: number | null
  stationsUsed: number
  status: ClassSessionStatus
}

export type UpsertOccurrencePayload = {
  shiftId: string
  date: string
  slot: number
  // 0 cancels the class
  stationsUsed: number
}

export type DayClass = {
  laboratoryId: string
  laboratoryName: string
  capacity: number
  shiftId: string
  shiftType: ShiftType
  slot: number
  startTime: string
  endTime: string
  gridStations: number | null
  stationsUsed: number
  status: ClassSessionStatus
}

export type DayClasses = {
  date: string
  periodId: string | null
  periodName: string | null
  schoolDay: boolean
  holidayName: string | null
  classes: DayClass[]
}

// --- API ---

export function listOccurrences(
  periodId: string,
  params: { laboratoryId?: string; from?: string; to?: string } = {},
): Promise<ClassOccurrence[]> {
  return api.get(`/academic-periods/${periodId}/occurrences`, { params }).then((r) => r.data)
}

/** Resolves to null when the value matches the weekly grid and no exception is kept. */
export function upsertOccurrence(
  periodId: string,
  labId: string,
  payload: UpsertOccurrencePayload,
): Promise<ClassOccurrence | null> {
  return api
    .put(`/academic-periods/${periodId}/laboratories/${labId}/occurrences`, payload)
    .then((r) => (r.status === 204 ? null : r.data))
}

export function deleteOccurrence(
  periodId: string,
  labId: string,
  params: { shiftId: string; date: string; slot: number },
): Promise<void> {
  return api
    .delete(`/academic-periods/${periodId}/laboratories/${labId}/occurrences`, { params })
    .then(() => undefined)
}

export function getDayClasses(date?: string): Promise<DayClasses> {
  return api.get('/class-sessions', { params: date ? { date } : {} }).then((r) => r.data)
}
