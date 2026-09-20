import { api } from './client.ts'

export type Laboratory = {
  id: string
  name: string
  description?: string
  active: boolean
  createdAt?: string
}

export type CreateLaboratoryPayload = {
  name: string
  description?: string
}

export type ListLaboratoriesOptions = {
  includeInactive?: boolean
}

export function listLaboratories(options: ListLaboratoriesOptions = {}): Promise<Laboratory[]> {
  return api
    .get('/laboratories', {
      params: options.includeInactive ? { active: false } : undefined,
    })
    .then((r) => r.data)
}

export function createLaboratory(payload: CreateLaboratoryPayload): Promise<Laboratory> {
  return api.post('/laboratories', payload).then((r) => r.data)
}

export function getLaboratory(id: string): Promise<Laboratory> {
  return api.get(`/laboratories/${id}`).then((r) => r.data)
}

export function updateLaboratory(
  id: string,
  payload: CreateLaboratoryPayload,
): Promise<Laboratory> {
  return api.put(`/laboratories/${id}`, payload).then((r) => r.data)
}

export function deactivateLaboratory(id: string): Promise<Laboratory> {
  return api.patch(`/laboratories/${id}/deactivate`).then((r) => r.data)
}

export function deleteLaboratory(id: string): Promise<void> {
  return api.delete(`/laboratories/${id}`).then(() => undefined)
}
