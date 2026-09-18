import { afterEach, describe, mock, test } from 'node:test'
import assert from 'node:assert/strict'
import { ConflictError } from './client.ts'
import {
  createLaboratory,
  deactivateLaboratory,
  deleteLaboratory,
  listLaboratories,
} from './laboratories.ts'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('laboratories api', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => {
    globalThis.fetch = originalFetch
  })

  // @spec:AC-006 Lista mostra apenas laboratórios ativos por padrão
  test('listLaboratories não envia query param por padrão', async () => {
    let requestUrl = ''
    globalThis.fetch = mock.fn(async (url: string) => {
      requestUrl = url
      return jsonResponse(200, [])
    }) as unknown as typeof fetch

    await listLaboratories()

    assert.equal(requestUrl, '/api/v1/laboratories')
  })

  // @spec:AC-007 Laboratórios inativos podem ser incluídos na listagem
  test('listLaboratories envia ?active=false quando includeInactive é true', async () => {
    let requestUrl = ''
    globalThis.fetch = mock.fn(async (url: string) => {
      requestUrl = url
      return jsonResponse(200, [])
    }) as unknown as typeof fetch

    await listLaboratories({ includeInactive: true })

    assert.equal(requestUrl, '/api/v1/laboratories?active=false')
  })

  // @spec:AC-004 Laboratório criado com nome
  test('createLaboratory faz POST em /api/v1/laboratories com o nome informado', async () => {
    let requestMethod = ''
    let requestBody: unknown
    globalThis.fetch = mock.fn(async (_url: string, init?: RequestInit) => {
      requestMethod = init?.method ?? 'GET'
      requestBody = init?.body ? JSON.parse(init.body as string) : undefined
      return jsonResponse(201, { id: '660e8400', name: 'LABCOMP-02', active: true })
    }) as unknown as typeof fetch

    const result = await createLaboratory({ name: 'LABCOMP-02' })

    assert.equal(requestMethod, 'POST')
    assert.deepEqual(requestBody, { name: 'LABCOMP-02' })
    assert.equal(result.name, 'LABCOMP-02')
  })

  // @spec:AC-011 Desativação preserva o laboratório
  test('deactivateLaboratory faz PATCH em /laboratories/{id}/deactivate', async () => {
    let requestUrl = ''
    let requestMethod = ''
    globalThis.fetch = mock.fn(async (url: string, init?: RequestInit) => {
      requestUrl = url
      requestMethod = init?.method ?? 'GET'
      return jsonResponse(200, { id: 'lab-1', name: 'LABCOMP-01', active: false })
    }) as unknown as typeof fetch

    const result = await deactivateLaboratory('lab-1')

    assert.equal(requestUrl, '/api/v1/laboratories/lab-1/deactivate')
    assert.equal(requestMethod, 'PATCH')
    assert.equal(result.active, false)
  })

  test('@spec:AC-012 exclusão bloqueada quando há dependentes — deleteLaboratory rejeita com ConflictError ao receber 409', async () => {
    globalThis.fetch = mock.fn(async () =>
      jsonResponse(409, { message: 'Laboratório possui registros dependentes; desative-o em vez de excluir' }),
    ) as unknown as typeof fetch

    await assert.rejects(() => deleteLaboratory('lab-1'), (error: unknown) => {
      assert.ok(error instanceof ConflictError)
      assert.equal((error as ConflictError).status, 409)
      return true
    })
  })

  test('@spec:AC-013 exclusão permitida quando não há dependentes — deleteLaboratory faz DELETE e resolve sem corpo em 204', async () => {
    let requestMethod = ''
    globalThis.fetch = mock.fn(async (_url: string, init?: RequestInit) => {
      requestMethod = init?.method ?? 'GET'
      return new Response(null, { status: 204 })
    }) as unknown as typeof fetch

    const result = await deleteLaboratory('lab-1')

    assert.equal(requestMethod, 'DELETE')
    assert.equal(result, undefined)
  })
})
