import axios from 'axios'

const INSTITUTION_STORAGE_KEY = 'carbon-calculator:institution-id'

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

function hasLocalStorage(): boolean {
  return typeof localStorage !== 'undefined' && typeof localStorage.getItem === 'function'
}

export function getActiveInstitutionId(): string | null {
  if (!hasLocalStorage()) return null
  return localStorage.getItem(INSTITUTION_STORAGE_KEY)
}

export function setActiveInstitutionId(institutionId: string | null): void {
  if (!hasLocalStorage()) return
  if (institutionId) {
    localStorage.setItem(INSTITUTION_STORAGE_KEY, institutionId)
  } else {
    localStorage.removeItem(INSTITUTION_STORAGE_KEY)
  }
}

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
  const institutionId = getActiveInstitutionId()
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
      const message = (data as { message?: string })?.message || error.message
      throw errorForStatus(status, message)
    }
    throw error
  },
)
