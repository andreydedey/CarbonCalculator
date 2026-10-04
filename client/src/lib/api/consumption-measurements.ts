import { api } from './client.ts'
import type { OperatingSystem } from './operating-systems'
import type { PageResponse } from './types'

export type TargetType = 'COMPUTER' | 'MONITOR' | 'COMBINED'

export type ConsumptionMeasurement = {
  id: string
  targetType: TargetType
  equipmentModel: { id: string; name: string } | null
  operatingSystem: OperatingSystem | null
  monitor: { id: string; name: string } | null
  averageWatts: number
  durationMinutes: number
  readingIntervalMinutes: number | null
  measurementDate: string
  conditions: string | null
  notes: string | null
  createdAt: string
}

export type OutlierWarning = {
  existingAverage: number
  newValue: number
  deviationPercent: number
}

export type ConsumptionMeasurementResponse = {
  measurement: ConsumptionMeasurement
  outlierWarning: OutlierWarning | null
}

export type CreateConsumptionMeasurementPayload = {
  targetType: TargetType
  equipmentModelId?: string | null
  operatingSystemId?: string | null
  monitorId?: string | null
  averageWatts: number
  durationMinutes: number
  readingIntervalMinutes?: number | null
  measurementDate: string
  conditions?: string | null
  notes?: string | null
}

export type ListConsumptionMeasurementsOptions = {
  targetType?: TargetType
  equipmentModelId?: string
  operatingSystemId?: string
  monitorId?: string
  page?: number
  size?: number
}

export function listConsumptionMeasurements(
  options: ListConsumptionMeasurementsOptions = {},
): Promise<PageResponse<ConsumptionMeasurement>> {
  const { page = 0, size = 10, ...filters } = options
  return api
    .get('/consumption-measurements', { params: { page, size, ...filters } })
    .then((r) => r.data)
}

export function createConsumptionMeasurement(
  payload: CreateConsumptionMeasurementPayload,
): Promise<ConsumptionMeasurementResponse> {
  return api.post('/consumption-measurements', payload).then((r) => r.data)
}

export function updateConsumptionMeasurement(
  id: string,
  payload: CreateConsumptionMeasurementPayload,
): Promise<ConsumptionMeasurementResponse> {
  return api.put(`/consumption-measurements/${id}`, payload).then((r) => r.data)
}

export function deleteConsumptionMeasurement(id: string): Promise<void> {
  return api.delete(`/consumption-measurements/${id}`).then(() => undefined)
}
