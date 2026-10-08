import { api } from './client'

export type InstitutionMembership = {
  institutionId: string
  name: string
  role: string
  status: string
}

export type UserProfile = {
  id: string
  name: string
  email: string
  admin: boolean
  institutions: InstitutionMembership[]
}

export type AuthResponse = {
  accessToken: string
  user: UserProfile
}

export type LoginPayload = {
  email: string
  password: string
}

export type InviteValidation = {
  email: string
  role: string
  institutionName: string
}

export type AcceptInvitePayload = {
  name: string
  password: string
}

export function login(payload: LoginPayload): Promise<AuthResponse> {
  return api.post('/auth/login', payload).then((r) => r.data)
}

export function validateInvite(token: string): Promise<InviteValidation> {
  return api.get(`/auth/invitations/${token}/validate`).then((r) => r.data)
}

export function acceptInvite(token: string, payload: AcceptInvitePayload): Promise<AuthResponse> {
  return api.post(`/auth/invitations/${token}/accept`, payload).then((r) => r.data)
}

export function refreshToken(): Promise<AuthResponse> {
  return api.post('/auth/refresh').then((r) => r.data)
}

export function getProfile(): Promise<UserProfile> {
  return api.get('/auth/me').then((r) => r.data)
}
