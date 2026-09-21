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

export type RegisterPayload = {
  name: string
  email: string
  password: string
}

export function login(payload: LoginPayload): Promise<AuthResponse> {
  return api.post('/auth/login', payload).then((r) => r.data)
}

export function register(payload: RegisterPayload): Promise<AuthResponse> {
  return api.post('/auth/register', payload).then((r) => r.data)
}

export function refreshToken(): Promise<AuthResponse> {
  return api.post('/auth/refresh').then((r) => r.data)
}

export function getProfile(): Promise<UserProfile> {
  return api.get('/auth/me').then((r) => r.data)
}
