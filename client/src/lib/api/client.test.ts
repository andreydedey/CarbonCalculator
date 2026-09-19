import assert from 'node:assert/strict'
import { afterEach, beforeEach, describe, test } from 'node:test'
import {
  ApiError,
  ConflictError,
  errorForStatus,
  getActiveInstitutionId,
  NotFoundError,
  setActiveInstitutionId,
  ValidationError,
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

describe('client', () => {
  const originalLocalStorage = (globalThis as { localStorage?: unknown }).localStorage

  beforeEach(() => {
    ;(globalThis as { localStorage?: unknown }).localStorage = new MemoryStorage()
  })

  afterEach(() => {
    ;(globalThis as { localStorage?: unknown }).localStorage = originalLocalStorage
  })

  test('@spec:AC-009 Requisição sem identificação de instituição é recusada — persiste a instituição ativa via getActiveInstitutionId', () => {
    setActiveInstitutionId('550e8400-e29b-41d4-a716-446655440000')
    assert.equal(getActiveInstitutionId(), '550e8400-e29b-41d4-a716-446655440000')
  })

  test('@spec:AC-009 Requisição sem identificação de instituição é recusada — retorna null sem instituição ativa', () => {
    setActiveInstitutionId(null)
    assert.equal(getActiveInstitutionId(), null)
  })

  test('setActiveInstitutionId com null limpa o valor salvo', () => {
    setActiveInstitutionId('institution-1')
    assert.equal(getActiveInstitutionId(), 'institution-1')

    setActiveInstitutionId(null)
    assert.equal(getActiveInstitutionId(), null)
  })

  test('@spec:AC-003 UF inválida é rejeitada — errorForStatus traduz 400 em ValidationError', () => {
    const error = errorForStatus(400, "UF inválida: 'XX'")

    assert.ok(error instanceof ValidationError)
    assert.equal(error.status, 400)
    assert.equal(error.message, "UF inválida: 'XX'")
  })

  test('@spec:AC-002 Sigla duplicada é rejeitada — errorForStatus traduz 409 em ConflictError', () => {
    const error = errorForStatus(409, "Já existe uma instituição com a sigla 'UFPA'")

    assert.ok(error instanceof ConflictError)
    assert.equal(error.status, 409)
  })

  test('@spec:AC-010 acesso direto a laboratório de outra instituição é negado — errorForStatus traduz 404 em NotFoundError', () => {
    const error = errorForStatus(404, 'não encontrado')

    assert.ok(error instanceof NotFoundError)
    assert.equal(error.status, 404)
  })

  test('errorForStatus retorna ApiError genérico para status não mapeados', () => {
    const error = errorForStatus(500, 'erro interno')

    assert.ok(error instanceof ApiError)
    assert.ok(!(error instanceof ValidationError))
    assert.ok(!(error instanceof ConflictError))
    assert.ok(!(error instanceof NotFoundError))
    assert.equal(error.status, 500)
  })
})
