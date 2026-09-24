import { api } from './client.ts'
import type { Monitor } from './monitors'
import type { PageResponse } from './types'

export type EquipmentModelSummary = {
  id: string
  name: string
  processor?: string
  tdpWatts?: number
  coreCount?: number
  memoryGb?: number
  hasIntegratedScreen: boolean
}

export type Configuration = {
  id: string
  equipmentModel: EquipmentModelSummary
  operatingSystem: string
  monitor: Monitor | null
  usageLabCount: number
  usageStationCount: number
  createdAt?: string
}

export type CreateConfigurationPayload = {
  equipmentModelId: string
  operatingSystem: string
  monitorId?: string | null
}

export type ListConfigurationsOptions = {
  page?: number
  size?: number
}

export function listConfigurations(
  options: ListConfigurationsOptions = {},
): Promise<PageResponse<Configuration>> {
  const { page = 0, size = 10 } = options
  return api.get('/configurations', { params: { page, size } }).then((r) => r.data)
}

export function createConfiguration(payload: CreateConfigurationPayload): Promise<Configuration> {
  return api.post('/configurations', payload).then((r) => r.data)
}

export function updateConfiguration(
  id: string,
  payload: CreateConfigurationPayload,
): Promise<Configuration> {
  return api.put(`/configurations/${id}`, payload).then((r) => r.data)
}

export function deleteConfiguration(id: string): Promise<void> {
  return api.delete(`/configurations/${id}`).then(() => undefined)
}
