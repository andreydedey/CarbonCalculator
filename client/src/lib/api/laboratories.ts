import { api } from './client.ts'
import type { PageResponse } from './types'

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

export type LabStatusFilter = 'all' | 'active' | 'inactive'

export type ListLaboratoriesOptions = {
  status?: LabStatusFilter
  search?: string
  page?: number
  size?: number
}

export function listLaboratories(options: ListLaboratoriesOptions = {}): Promise<PageResponse<Laboratory>> {
  const { status, search, page = 0, size = 10 } = options
  const activeParam =
    status === 'active' ? { active: true } : status === 'inactive' ? { active: false } : {}
  return api
    .get('/laboratories', {
      params: {
        ...activeParam,
        ...(search ? { name: search } : {}),
        page,
        size,
      },
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

export function activateLaboratory(id: string): Promise<Laboratory> {
  return api.patch(`/laboratories/${id}/activate`).then((r) => r.data)
}

export function deactivateLaboratory(id: string): Promise<Laboratory> {
  return api.patch(`/laboratories/${id}/deactivate`).then((r) => r.data)
}

export function deleteLaboratory(id: string): Promise<void> {
  return api.delete(`/laboratories/${id}`).then(() => undefined)
}
