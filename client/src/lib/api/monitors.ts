import { api } from './client.ts'
import type { PageResponse } from './types'

export type Monitor = {
  id: string
  name: string
  watts?: number
  createdAt?: string
}

export type CreateMonitorPayload = {
  name: string
  watts?: number
}

export type ListMonitorsOptions = {
  name?: string
  page?: number
  size?: number
}

export function listMonitors(
  options: ListMonitorsOptions = {},
): Promise<PageResponse<Monitor>> {
  const { name, page = 0, size = 10 } = options
  return api
    .get('/monitors', {
      params: {
        ...(name ? { name } : {}),
        page,
        size,
      },
    })
    .then((r) => r.data)
}

export function getMonitor(id: string): Promise<Monitor> {
  return api.get(`/monitors/${id}`).then((r) => r.data)
}

export function createMonitor(payload: CreateMonitorPayload): Promise<Monitor> {
  return api.post('/monitors', payload).then((r) => r.data)
}

export function updateMonitor(id: string, payload: CreateMonitorPayload): Promise<Monitor> {
  return api.put(`/monitors/${id}`, payload).then((r) => r.data)
}

export function deleteMonitor(id: string): Promise<void> {
  return api.delete(`/monitors/${id}`).then(() => undefined)
}
