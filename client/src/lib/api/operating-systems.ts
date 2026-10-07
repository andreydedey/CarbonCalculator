import { api } from './client.ts'
import type { PageResponse } from './types'

export type OperatingSystem = {
  id: string
  name: string
}

export type CreateOperatingSystemPayload = {
  name: string
}

export type ListOperatingSystemsOptions = {
  name?: string
  page?: number
  size?: number
}

export function listOperatingSystems(
  options: ListOperatingSystemsOptions = {},
): Promise<PageResponse<OperatingSystem>> {
  const { name, page = 0, size = 100 } = options
  return api.get('/operating-systems', { params: { name, page, size } }).then((r) => r.data)
}

export function createOperatingSystem(
  payload: CreateOperatingSystemPayload,
): Promise<OperatingSystem> {
  return api.post('/operating-systems', payload).then((r) => r.data)
}

export function updateOperatingSystem(
  id: string,
  payload: CreateOperatingSystemPayload,
): Promise<OperatingSystem> {
  return api.put(`/operating-systems/${id}`, payload).then((r) => r.data)
}

export function deleteOperatingSystem(id: string): Promise<void> {
  return api.delete(`/operating-systems/${id}`).then(() => undefined)
}
