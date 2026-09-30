import { api } from './client.ts'

// --- Types ---

export type EmissionResult = {
  period: { id: string; name: string; startDate: string; endDate: string }
  totalEmissionKg: number
  totalEnergyKwh: number
  equivalences: { carKm: number; treesNeeded: number }
  byMonth: MonthEmission[]
  byLaboratory: LaboratoryEmission[]
  byShift: ShiftEmission[]
  byDayOfWeek: DayOfWeekEmission[]
  byEquipmentModel: EquipmentModelEmission[]
  byMonitorModel: MonitorModelEmission[]
  byOperatingSystem: OperatingSystemEmission[]
  inputs: {
    emissionFactors: { month: string; value: number; source: string }[]
    consumptionSources: {
      configurationId: string
      label: string
      source: string
      computerWatts: number
      monitorWatts: number
      totalWatts: number
    }[]
  }
}

export type MonthEmission = {
  month: string
  energyKwh: number
  emissionKg: number
  emissionFactor: number | null
  schoolDays: number
}

export type LaboratoryEmission = {
  laboratoryId: string
  laboratoryName: string
  energyKwh: number
  emissionKg: number
  stationCount: number
  computerEmissionKg: number
  monitorEmissionKg: number
  byMonth: MonthEmission[]
  configurations: ConfigurationEmission[]
}

export type ConfigurationEmission = {
  configurationId: string
  label: string
  quantity: number
  consumptionWatts: number
  consumptionSource: string
  computerWatts: number
  monitorWatts: number
  energyKwh: number
  emissionKg: number
}

export type ShiftEmission = {
  shiftType: string
  energyKwh: number
  emissionKg: number
}

export type DayOfWeekEmission = {
  dayOfWeek: number
  label: string
  energyKwh: number
  emissionKg: number
}

export type EquipmentModelEmission = {
  modelId: string
  modelName: string
  emissionKg: number
  percentage: number
}

export type MonitorModelEmission = {
  monitorId: string
  monitorName: string
  emissionKg: number
  percentage: number
}

export type OperatingSystemEmission = {
  operatingSystem: string
  emissionKg: number
  percentage: number
}

export type ReadinessResult = {
  ready: boolean
  missingEmissionFactors: string[]
  laboratoriesWithoutEquipment: string[]
  laboratoriesWithoutSchedule: string[]
  configurationsWithoutMonitor: {
    configurationId: string
    label: string
    laboratoryNames: string[]
  }[]
}

// --- API ---

export function getEmissions(periodId: string): Promise<EmissionResult> {
  return api.get(`/academic-periods/${periodId}/emissions`).then((r) => r.data)
}

export function getReadiness(periodId: string): Promise<ReadinessResult> {
  return api.get(`/academic-periods/${periodId}/emissions/readiness`).then((r) => r.data)
}

export function getEmissionsCsvUrl(periodId: string): string {
  const baseUrl = api.defaults.baseURL ?? '/api/v1'
  return `${baseUrl}/academic-periods/${periodId}/emissions/export`
}
