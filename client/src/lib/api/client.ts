import axios, { type InternalAxiosRequestConfig } from 'axios'
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

export function isApiError(error: unknown): error is ApiError {
  return error instanceof Error && 'status' in error && typeof error.status === 'number'
}

export function errorForStatus(status: number, message: string): ApiError {
  if (status === 400) return new ValidationError(status, message)
  if (status === 404) return new NotFoundError(status, message)
  if (status === 409) return new ConflictError(status, message)
  return new ApiError(status, message)
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  const institutionId = readStoredInstitutionId()
  if (institutionId) {
    config.headers['X-Institution-Id'] = institutionId
  }
  config.headers['X-Correlation-ID'] = crypto.randomUUID()
  return config
})

// --- 401 refresh interceptor (runs before error mapping) ---
let isRefreshing = false
let failedQueue: { resolve: (value: unknown) => void; reject: (reason: unknown) => void }[] = []

function processQueue(error: unknown, token: string | null) {
  for (const { resolve, reject } of failedQueue) {
    if (token) resolve(token)
    else reject(error)
  }
  failedQueue = []
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      return Promise.reject(error)
    }

    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    if (!originalRequest || originalRequest._retry) {
      return Promise.reject(error)
    }

    const url = originalRequest.url ?? ''
    if (
      url.includes('/auth/login') ||
      url.includes('/auth/refresh') ||
      url.includes('/auth/logout')
    ) {
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({
          resolve: (token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(api.request(originalRequest))
          },
          reject,
        })
      })
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      const { data } = await api.post<{ accessToken: string }>('/auth/refresh')
      const newToken = data.accessToken
      api.defaults.headers.common.Authorization = `Bearer ${newToken}`
      processQueue(null, newToken)
      originalRequest.headers.Authorization = `Bearer ${newToken}`
      return api.request(originalRequest)
    } catch (refreshError) {
      processQueue(refreshError, null)
      delete api.defaults.headers.common.Authorization
      window.location.href = '/login'
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

// --- Error mapping interceptor ---
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response) {
      const { status, data, config: reqConfig } = error.response
      const body = data as { message?: string; error?: string }
      const message = body?.message || body?.error || error.message
      const correlationId = reqConfig?.headers?.['X-Correlation-ID'] ?? 'unknown'
      const method = reqConfig?.method?.toUpperCase() ?? '?'
      const url = reqConfig?.url ?? '?'
      console.error(`[${correlationId}] API error ${status} on ${method} ${url}: ${message}`)
      throw errorForStatus(status, message)
    }
    throw error
  },
)
