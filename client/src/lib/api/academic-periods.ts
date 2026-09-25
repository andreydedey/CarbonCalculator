import { api } from './client.ts'
import type { PageResponse } from './types'

// --- Types ---

export type AcademicPeriod = {
  id: string
  name: string
  startDate: string
  endDate: string
  holidayCount: number
  createdAt: string
}

export type Holiday = {
  date: string
  description?: string
}

export type ScheduleBlock = {
  dayOfWeek: number
  startTime: string
  endTime: string
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

// --- API functions ---

export function listAcademicPeriods(
  page = 0,
  size = 10,
): Promise<PageResponse<AcademicPeriod>> {
  return api.get('/academic-periods', { params: { page, size } }).then((r) => r.data)
}

export function getAcademicPeriod(id: string): Promise<AcademicPeriod> {
  return api.get(`/academic-periods/${id}`).then((r) => r.data)
}

export function createAcademicPeriod(payload: CreateAcademicPeriodPayload): Promise<AcademicPeriod> {
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

export function replaceHolidays(periodId: string, holidays: Holiday[]): Promise<Holiday[]> {
  return api.put(`/academic-periods/${periodId}/holidays`, { holidays }).then((r) => r.data)
}

export function copyPeriod(sourcePeriodId: string, payload: CopyPeriodPayload): Promise<AcademicPeriod> {
  return api.post(`/academic-periods/${sourcePeriodId}/copy`, payload).then((r) => r.data)
}

export function getSchedule(periodId: string, labId: string): Promise<ScheduleBlock[]> {
  return api
    .get(`/academic-periods/${periodId}/laboratories/${labId}/schedule`)
    .then((r) => r.data)
}

export function replaceSchedule(
  periodId: string,
  labId: string,
  blocks: ScheduleBlock[],
): Promise<ScheduleBlock[]> {
  return api
    .put(`/academic-periods/${periodId}/laboratories/${labId}/schedule`, { blocks })
    .then((r) => r.data)
}

export function getPeriodSummary(periodId: string): Promise<PeriodSummary> {
  return api.get(`/academic-periods/${periodId}/summary`).then((r) => r.data)
}
