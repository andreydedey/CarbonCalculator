import { api } from './client.ts'

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
  return api.post('/institutions', payload).then((r) => r.data)
}

export function listInstitutions(): Promise<Institution[]> {
  return api.get('/institutions').then((r) => r.data)
}

export function getInstitution(id: string): Promise<Institution> {
  return api.get(`/institutions/${id}`).then((r) => r.data)
}

export function updateInstitution(id: string, payload: UpdateInstitutionPayload): Promise<Institution> {
  return api.put(`/institutions/${id}`, payload).then((r) => r.data)
}
