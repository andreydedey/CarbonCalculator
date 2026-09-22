import { api } from './client.ts'

export type EquipmentModelSummary = {
  id: string
  name: string
  processor?: string
  tdpWatts?: number
  coreCount?: number
  memoryGb?: number
  monitorName?: string
  monitorWatts?: number
  operatingSystem?: string
  hasMonitor: boolean
}

export type LaboratoryEquipment = {
  id: string
  equipmentModel: EquipmentModelSummary
  operatingSystem: string
  quantity: number
  createdAt?: string
}

export type LaboratoryComposition = {
  items: LaboratoryEquipment[]
  totalMachines: number
  modelsWithoutMonitor: number
}

export type CreateLaboratoryEquipmentPayload = {
  equipmentModelId: string
  operatingSystem: string
  quantity: number
}

export function getLabComposition(labId: string): Promise<LaboratoryComposition> {
  return api.get(`/laboratories/${labId}/equipment`).then((r) => r.data)
}

export function linkEquipment(
  labId: string,
  payload: CreateLaboratoryEquipmentPayload,
): Promise<LaboratoryEquipment> {
  return api.post(`/laboratories/${labId}/equipment`, payload).then((r) => r.data)
}

export function updateLabEquipment(
  labId: string,
  id: string,
  payload: CreateLaboratoryEquipmentPayload,
): Promise<LaboratoryEquipment> {
  return api.put(`/laboratories/${labId}/equipment/${id}`, payload).then((r) => r.data)
}

export function unlinkEquipment(labId: string, id: string): Promise<void> {
  return api.delete(`/laboratories/${labId}/equipment/${id}`).then(() => undefined)
}
