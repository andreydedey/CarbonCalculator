/**
 * HTTP client central da aplicação. Injeta o header `X-Institution-Id`
 * (multi-tenancy via RLS, ver ADR-004 e TDD 02) em toda requisição que não
 * seja de instituição, e traduz respostas de erro HTTP em exceções tipadas.
 */

const BASE_URL = '/api/v1'
const INSTITUTION_HEADER = 'X-Institution-Id'
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
  if (!hasLocalStorage()) {
    return null
  }
  return localStorage.getItem(INSTITUTION_STORAGE_KEY)
}

export function setActiveInstitutionId(institutionId: string | null): void {
  if (!hasLocalStorage()) {
    return
  }
  if (institutionId) {
    localStorage.setItem(INSTITUTION_STORAGE_KEY, institutionId)
  } else {
    localStorage.removeItem(INSTITUTION_STORAGE_KEY)
  }
}

type Query = Record<string, string | boolean | undefined>

export type RequestConfig = {
  method?: string
  body?: unknown
  query?: Query
  skipInstitutionHeader?: boolean
}

function buildQueryString(query?: Query): string {
  if (!query) {
    return ''
  }
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) {
      params.set(key, String(value))
    }
  }
  const queryString = params.toString()
  return queryString ? `?${queryString}` : ''
}

function errorForStatus(status: number, message: string): ApiError {
  if (status === 400) {
    return new ValidationError(status, message)
  }
  if (status === 404) {
    return new NotFoundError(status, message)
  }
  if (status === 409) {
    return new ConflictError(status, message)
  }
  return new ApiError(status, message)
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const data = await response.json()
    if (data && typeof data === 'object' && typeof data.message === 'string') {
      return data.message
    }
  } catch {
    // corpo da resposta não é JSON — segue com a mensagem padrão
  }
  return response.statusText || `Erro HTTP ${response.status}`
}

/**
 * Executa uma requisição contra a API, injetando o header de instituição
 * ativa (AC-009) e convertendo erros HTTP em exceções tipadas (AC-002,
 * AC-003, AC-010).
 */
export async function apiRequest<T>(path: string, config: RequestConfig = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }

  if (!config.skipInstitutionHeader) {
    const institutionId = getActiveInstitutionId()
    if (institutionId) {
      headers[INSTITUTION_HEADER] = institutionId
    }
  }

  const response = await fetch(`${BASE_URL}${path}${buildQueryString(config.query)}`, {
    method: config.method ?? 'GET',
    headers,
    body: config.body !== undefined ? JSON.stringify(config.body) : undefined,
  })

  if (!response.ok) {
    throw errorForStatus(response.status, await readErrorMessage(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
