import { afterEach, beforeEach, describe, mock, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  apiRequest,
  ApiError,
  ConflictError,
  NotFoundError,
  ValidationError,
  getActiveInstitutionId,
  setActiveInstitutionId,
} from './client.ts'

class MemoryStorage {
  private store = new Map<string, string>()

  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) as string) : null
  }

  setItem(key: string, value: string): void {
    this.store.set(key, value)
  }

  removeItem(key: string): void {
    this.store.delete(key)
  }
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiRequest', () => {
  const originalFetch = globalThis.fetch
  const originalLocalStorage = (globalThis as { localStorage?: unknown }).localStorage

  beforeEach(() => {
    ;(globalThis as { localStorage?: unknown }).localStorage = new MemoryStorage()
  })

  afterEach(() => {
    globalThis.fetch = originalFetch
    ;(globalThis as { localStorage?: unknown }).localStorage = originalLocalStorage
  })

  // @spec:AC-009 Requisição sem identificação de instituição é recusada
  test('injeta o header X-Institution-Id quando há instituição ativa', async () => {
    setActiveInstitutionId('550e8400-e29b-41d4-a716-446655440000')
    let receivedHeaders: Headers | undefined
    globalThis.fetch = mock.fn(async (_url: string, init?: RequestInit) => {
      receivedHeaders = new Headers(init?.headers)
      return jsonResponse(200, [])
    }) as unknown as typeof fetch

    await apiRequest('/laboratories')

    assert.equal(receivedHeaders?.get('X-Institution-Id'), '550e8400-e29b-41d4-a716-446655440000')
  })

  // @spec:AC-009 Requisição sem identificação de instituição é recusada
  test('não envia o header X-Institution-Id quando não há instituição ativa', async () => {
    setActiveInstitutionId(null)
    let receivedHeaders: Headers | undefined
    globalThis.fetch = mock.fn(async (_url: string, init?: RequestInit) => {
      receivedHeaders = new Headers(init?.headers)
      return jsonResponse(200, [])
    }) as unknown as typeof fetch

    await apiRequest('/laboratories')

    assert.equal(receivedHeaders?.has('X-Institution-Id'), false)
  })

  test('não envia o header de instituição quando skipInstitutionHeader é true', async () => {
    setActiveInstitutionId('550e8400-e29b-41d4-a716-446655440000')
    let receivedHeaders: Headers | undefined
    globalThis.fetch = mock.fn(async (_url: string, init?: RequestInit) => {
      receivedHeaders = new Headers(init?.headers)
      return jsonResponse(200, {})
    }) as unknown as typeof fetch

    await apiRequest('/institutions', { skipInstitutionHeader: true })

    assert.equal(receivedHeaders?.has('X-Institution-Id'), false)
  })

  // @spec:AC-003 UF inválida é rejeitada
  test('traduz resposta 400 em ValidationError', async () => {
    globalThis.fetch = mock.fn(async () => jsonResponse(400, { message: "UF inválida: 'XX'" })) as unknown as typeof fetch

    await assert.rejects(() => apiRequest('/institutions', { skipInstitutionHeader: true }), (error: unknown) => {
      assert.ok(error instanceof ValidationError)
      assert.equal((error as ValidationError).status, 400)
      assert.equal((error as ValidationError).message, "UF inválida: 'XX'")
      return true
    })
  })

  // @spec:AC-002 Sigla duplicada é rejeitada
  test('traduz resposta 409 em ConflictError', async () => {
    globalThis.fetch = mock.fn(async () =>
      jsonResponse(409, { message: "Já existe uma instituição com a sigla 'UFPA'" }),
    ) as unknown as typeof fetch

    await assert.rejects(() => apiRequest('/institutions', { skipInstitutionHeader: true }), (error: unknown) => {
      assert.ok(error instanceof ConflictError)
      assert.equal((error as ConflictError).status, 409)
      return true
    })
  })

  test('@spec:AC-010 acesso direto a laboratório de outra instituição é negado — traduz resposta 404 em NotFoundError', async () => {
    globalThis.fetch = mock.fn(async () => jsonResponse(404, { message: 'não encontrado' })) as unknown as typeof fetch

    await assert.rejects(() => apiRequest('/laboratories/does-not-exist'), (error: unknown) => {
      assert.ok(error instanceof NotFoundError)
      assert.equal((error as NotFoundError).status, 404)
      return true
    })
  })

  test('lança ApiError genérico para status não mapeados', async () => {
    globalThis.fetch = mock.fn(async () => new Response('erro interno', { status: 500 })) as unknown as typeof fetch

    await assert.rejects(() => apiRequest('/laboratories'), (error: unknown) => {
      assert.ok(error instanceof ApiError)
      assert.equal((error as ApiError).status, 500)
      return true
    })
  })

  test('retorna undefined para respostas 204', async () => {
    globalThis.fetch = mock.fn(async () => new Response(null, { status: 204 })) as unknown as typeof fetch

    const result = await apiRequest('/laboratories/some-id')

    assert.equal(result, undefined)
  })

  test('persiste a instituição ativa entre chamadas via getActiveInstitutionId', () => {
    setActiveInstitutionId('institution-1')
    assert.equal(getActiveInstitutionId(), 'institution-1')

    setActiveInstitutionId(null)
    assert.equal(getActiveInstitutionId(), null)
  })
})
