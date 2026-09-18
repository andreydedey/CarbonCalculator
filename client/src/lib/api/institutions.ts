import { apiRequest } from './client.ts'

/**
 * Endpoints de instituição (US-001). Não enviam o header
 * `X-Institution-Id` — a tabela `institution` não tem RLS (ver TDD 02).
 */

export type CreateLaboratoryPayload = {
  name: string
}

export type CreateInstitutionPayload = {
  name: string
  acronym: string
  city?: string
  state: string
  laboratory: CreateLaboratoryPayload
}

export type UpdateInstitutionPayload = {
  name: string
  acronym: string
  city?: string
  state: string
}

export type Institution = {
  id: string
  name: string
  acronym: string
  city: string | null
  state: string
  active: boolean
  createdAt: string
}

export function createInstitution(payload: CreateInstitutionPayload): Promise<Institution> {
  return apiRequest<Institution>('/institutions', {
    method: 'POST',
    body: payload,
    skipInstitutionHeader: true,
  })
}

export function listInstitutions(): Promise<Institution[]> {
  return apiRequest<Institution[]>('/institutions', { skipInstitutionHeader: true })
}

export function getInstitution(id: string): Promise<Institution> {
  return apiRequest<Institution>(`/institutions/${id}`, { skipInstitutionHeader: true })
}

export function updateInstitution(id: string, payload: UpdateInstitutionPayload): Promise<Institution> {
  return apiRequest<Institution>(`/institutions/${id}`, {
    method: 'PUT',
    body: payload,
    skipInstitutionHeader: true,
  })
}
