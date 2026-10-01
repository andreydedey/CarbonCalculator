import { api } from './client.ts'
import type { PageResponse } from './types'

// --- Types ---

export type EmissionFactor = {
  id: string
  referenceMonth: string
  value: number
  source: string
  createdAt: string
}

export type CreateEmissionFactorPayload = {
  referenceMonth: string
  value: number
  source: string
}

export type ListEmissionFactorsOptions = {
  year?: number
  page?: number
  size?: number
}

// --- API ---

export function listEmissionFactors(
  options: ListEmissionFactorsOptions = {},
): Promise<PageResponse<EmissionFactor>> {
  return api
    .get('/emission-factors', {
      params: {
        ...(options.year != null && { year: options.year }),
        ...(options.page != null && { page: options.page }),
        ...(options.size != null && { size: options.size }),
      },
    })
    .then((r) => r.data)
}

export function createEmissionFactor(
  payload: CreateEmissionFactorPayload,
): Promise<EmissionFactor> {
  return api.post('/emission-factors', payload).then((r) => r.data)
}

export function updateEmissionFactor(
  id: string,
  payload: CreateEmissionFactorPayload,
): Promise<EmissionFactor> {
  return api.put(`/emission-factors/${id}`, payload).then((r) => r.data)
}

export function deleteEmissionFactor(id: string): Promise<void> {
  return api.delete(`/emission-factors/${id}`).then(() => undefined)
}
