import { afterEach, describe, mock, test } from 'node:test'
import assert from 'node:assert/strict'
import { createInstitution, listInstitutions } from './institutions.ts'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('institutions api', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  // @spec:AC-001 Instituição criada com dados válidos
  test('createInstitution envia instituição e laboratório vinculado numa única requisição', async () => {
    let requestUrl = ''
    let requestMethod = ''
    let requestBody: unknown
    globalThis.fetch = mock.fn(async (url: string, init?: RequestInit) => {
      requestUrl = url
      requestMethod = init?.method ?? 'GET'
      requestBody = init?.body ? JSON.parse(init.body as string) : undefined
      return jsonResponse(201, {
        id: '550e8400-e29b-41d4-a716-446655440000',
        name: 'Universidade Federal do Pará',
        acronym: 'UFPA',
        city: 'Belém',
        state: 'PA',
        active: true,
        createdAt: '2026-09-18T10:00:00Z',
      })
    }) as unknown as typeof fetch

    const result = await createInstitution({
      name: 'Universidade Federal do Pará',
      acronym: 'UFPA',
      city: 'Belém',
      state: 'PA',
      laboratory: { name: 'LABCOMP-01' },
    })

    assert.equal(requestUrl, '/api/v1/institutions')
    assert.equal(requestMethod, 'POST')
    assert.deepEqual(requestBody, {
      name: 'Universidade Federal do Pará',
      acronym: 'UFPA',
      city: 'Belém',
      state: 'PA',
      laboratory: { name: 'LABCOMP-01' },
    })
    assert.equal(result.acronym, 'UFPA')
  })

  test('listInstitutions faz GET em /api/v1/institutions', async () => {
    let requestUrl = ''
    globalThis.fetch = mock.fn(async (url: string) => {
      requestUrl = url
      return jsonResponse(200, [])
    }) as unknown as typeof fetch

    const result = await listInstitutions()

    assert.equal(requestUrl, '/api/v1/institutions')
    assert.deepEqual(result, [])
  })
})
