import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  ApiError,
  ConflictError,
  errorForStatus,
  NotFoundError,
  ValidationError,
} from './client.ts'

describe('client', () => {
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
