import { api } from './client.ts'
import type { PageResponse } from './types'

export type EquipmentModel = {
  id: string
  name: string
  processor?: string
  memoryGb?: number
  hasDedicatedGpu: boolean
  gpuModel?: string
  monitorName?: string
  monitorSizeInches?: number
  monitorResolution?: string
  hasMonitor: boolean
  createdAt?: string
}

export type CreateEquipmentModelPayload = {
  name: string
  processor?: string
  memoryGb?: number
  hasDedicatedGpu?: boolean
  gpuModel?: string
  monitorName?: string
  monitorSizeInches?: number
  monitorResolution?: string
}

export type ListEquipmentModelsOptions = {
  name?: string
  page?: number
  size?: number
}

export function listEquipmentModels(
  options: ListEquipmentModelsOptions = {},
): Promise<PageResponse<EquipmentModel>> {
  const { name, page = 0, size = 10 } = options
  return api
    .get('/equipment-models', {
      params: {
        ...(name ? { name } : {}),
        page,
        size,
      },
    })
    .then((r) => r.data)
}

export function getEquipmentModel(id: string): Promise<EquipmentModel> {
  return api.get(`/equipment-models/${id}`).then((r) => r.data)
}

export function createEquipmentModel(
  payload: CreateEquipmentModelPayload,
): Promise<EquipmentModel> {
  return api.post('/equipment-models', payload).then((r) => r.data)
}

export function updateEquipmentModel(
  id: string,
  payload: CreateEquipmentModelPayload,
): Promise<EquipmentModel> {
  return api.put(`/equipment-models/${id}`, payload).then((r) => r.data)
}

export function deleteEquipmentModel(id: string): Promise<void> {
  return api.delete(`/equipment-models/${id}`).then(() => undefined)
}
