import { api } from './client'
import type { PageResponse } from './types'

export type UserMember = {
  id: string
  name: string | null
  email: string
  role: string
  status: string
}

export type InvitePayload = {
  email: string
  role: string
}

export type ChangeRolePayload = {
  role: string
}

export function listMembers(): Promise<PageResponse<UserMember>> {
  return api.get('/users').then((r) => r.data)
}

export function inviteUser(payload: InvitePayload): Promise<UserMember> {
  return api.post('/users/invite', payload).then((r) => r.data)
}

export function changeRole(id: string, payload: ChangeRolePayload): Promise<UserMember> {
  return api.patch(`/users/${id}/role`, payload).then((r) => r.data)
}

export function revokeAccess(id: string): Promise<void> {
  return api.delete(`/users/${id}`).then(() => undefined)
}
