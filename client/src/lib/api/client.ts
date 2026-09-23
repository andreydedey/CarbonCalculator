import axios from 'axios'
import { readStoredInstitutionId } from '@/lib/context/institutionStorage'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export class ValidationError extends ApiError {}
export class ConflictError extends ApiError {}
export class NotFoundError extends ApiError {}

export function errorForStatus(status: number, message: string): ApiError {
  if (status === 400) return new ValidationError(status, message)
  if (status === 404) return new NotFoundError(status, message)
  if (status === 409) return new ConflictError(status, message)
  return new ApiError(status, message)
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
})

api.interceptors.request.use((config) => {
  const institutionId = readStoredInstitutionId()
  if (institutionId) {
    config.headers['X-Institution-Id'] = institutionId
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response) {
      const { status, data } = error.response
      const body = data as { message?: string; error?: string }
      const message = body?.message || body?.error || error.message
      throw errorForStatus(status, message)
    }
    throw error
  },
)
