import { api } from './client.ts'
import type { PageResponse } from './types'

// --- Types ---

export type ShiftType = 'MORNING' | 'AFTERNOON' | 'EVENING'

export type HolidayType = 'NATIONAL' | 'STATE' | 'MUNICIPAL' | 'RECESS'

export type Shift = {
  id: string
  shiftType: ShiftType
  startTime: string
  endTime: string
  classesPerDay: number
  classDurationMinutes: number
  breakDurationMinutes: number
  activeDays: number[]
  enabled: boolean
}

export type AcademicPeriod = {
  id: string
  name: string
  startDate: string
  endDate: string
  holidayCount: number
  shifts: Shift[]
}

export type Holiday = {
  date: string
  description: string
  type: HolidayType
}

export type ScheduleEntry = {
  shiftId: string
  shiftType: ShiftType
  dayOfWeek: number
  occupiedSlots: number[]
}

export type MonthSchoolDays = {
  month: string
  schoolDays: number
}

export type MonthHours = {
  month: string
  hours: number
}

export type LaboratorySummary = {
  laboratoryId: string
  laboratoryName: string
  hoursPerMonth: MonthHours[]
  totalHours: number
}

export type PeriodSummary = {
  period: {
    id: string
    name: string
    startDate: string
    endDate: string
  }
  schoolDaysPerMonth: MonthSchoolDays[]
  laboratorySummaries: LaboratorySummary[]
}

export type CreateAcademicPeriodPayload = {
  name: string
  startDate: string
  endDate: string
}

export type UpdateAcademicPeriodPayload = {
  name: string
  startDate: string
  endDate: string
}

export type CopyPeriodPayload = {
  name: string
  startDate: string
  endDate: string
}

export type ShiftInput = {
  shiftType: ShiftType
  startTime: string
  classesPerDay: number
  classDurationMinutes: number
  breakDurationMinutes: number
  activeDays: number[]
  enabled: boolean
}

export type ScheduleInput = {
  shiftId: string
  dayOfWeek: number
  occupiedSlots: number[]
}

// --- API functions ---

export function listAcademicPeriods(page = 0, size = 10): Promise<PageResponse<AcademicPeriod>> {
  return api.get('/academic-periods', { params: { page, size } }).then((r) => r.data)
}

export function getAcademicPeriod(id: string): Promise<AcademicPeriod> {
  return api.get(`/academic-periods/${id}`).then((r) => r.data)
}

export function createAcademicPeriod(
  payload: CreateAcademicPeriodPayload,
): Promise<AcademicPeriod> {
  return api.post('/academic-periods', payload).then((r) => r.data)
}

export function updateAcademicPeriod(
  id: string,
  payload: UpdateAcademicPeriodPayload,
): Promise<AcademicPeriod> {
  return api.put(`/academic-periods/${id}`, payload).then((r) => r.data)
}

export function deleteAcademicPeriod(id: string): Promise<void> {
  return api.delete(`/academic-periods/${id}`).then(() => undefined)
}

export function replaceShifts(periodId: string, shifts: ShiftInput[]): Promise<Shift[]> {
  return api.put(`/academic-periods/${periodId}/shifts`, { shifts }).then((r) => r.data)
}

export function getHolidays(periodId: string): Promise<Holiday[]> {
  return api.get(`/academic-periods/${periodId}/holidays`).then((r) => r.data)
}

export function replaceHolidays(periodId: string, holidays: Holiday[]): Promise<Holiday[]> {
  return api.put(`/academic-periods/${periodId}/holidays`, { holidays }).then((r) => r.data)
}

export function copyPeriod(
  sourcePeriodId: string,
  payload: CopyPeriodPayload,
): Promise<AcademicPeriod> {
  return api.post(`/academic-periods/${sourcePeriodId}/copy`, payload).then((r) => r.data)
}

export function getSchedule(periodId: string, labId: string): Promise<ScheduleEntry[]> {
  return api.get(`/academic-periods/${periodId}/laboratories/${labId}/schedule`).then((r) => r.data)
}

export function replaceSchedule(
  periodId: string,
  labId: string,
  entries: ScheduleInput[],
): Promise<ScheduleEntry[]> {
  return api
    .put(`/academic-periods/${periodId}/laboratories/${labId}/schedule`, { entries })
    .then((r) => r.data)
}

export function getPeriodSummary(periodId: string): Promise<PeriodSummary> {
  return api.get(`/academic-periods/${periodId}/summary`).then((r) => r.data)
}
