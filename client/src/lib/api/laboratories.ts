import { apiRequest } from './client.ts'

/**
 * Endpoints de laboratório (US-002, US-003, US-004, US-005). Filtrados
 * automaticamente pelo RLS a partir do header `X-Institution-Id` injetado
 * pelo client (ver TDD 02).
 */

export type Laboratory = {
  id: string
  name: string
  active: boolean
  createdAt?: string
}

export type CreateLaboratoryPayload = {
  name: string
}

export type ListLaboratoriesOptions = {
  includeInactive?: boolean
}

export function listLaboratories(options: ListLaboratoriesOptions = {}): Promise<Laboratory[]> {
  return apiRequest<Laboratory[]>('/laboratories', {
    query: options.includeInactive ? { active: false } : undefined,
  })
}

export function createLaboratory(payload: CreateLaboratoryPayload): Promise<Laboratory> {
  return apiRequest<Laboratory>('/laboratories', { method: 'POST', body: payload })
}

export function getLaboratory(id: string): Promise<Laboratory> {
  return apiRequest<Laboratory>(`/laboratories/${id}`)
}

export function updateLaboratory(id: string, payload: CreateLaboratoryPayload): Promise<Laboratory> {
  return apiRequest<Laboratory>(`/laboratories/${id}`, { method: 'PUT', body: payload })
}

export function deactivateLaboratory(id: string): Promise<Laboratory> {
  return apiRequest<Laboratory>(`/laboratories/${id}/deactivate`, { method: 'PATCH' })
}

export function deleteLaboratory(id: string): Promise<void> {
  return apiRequest<void>(`/laboratories/${id}`, { method: 'DELETE' })
}
