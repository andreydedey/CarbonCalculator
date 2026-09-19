import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mapCreateInstitutionError } from './mapCreateInstitutionError.ts'

test('@spec:AC-002 Sigla duplicada é rejeitada — erro 409 vira erro no campo Sigla com aviso de uso duplicado', () => {
  const result = mapCreateInstitutionError({
    status: 409,
    message: 'Sigla já está em uso por outra instituição.',
  })

  assert.deepEqual(result, {
    field: 'acronym',
    message: 'Sigla já está em uso por outra instituição.',
  })
})

test('@spec:AC-002 Sigla duplicada é rejeitada — mensagem padrão é usada quando o erro 409 não traz mensagem', () => {
  const result = mapCreateInstitutionError({ status: 409, message: '' })

  assert.equal(result.field, 'acronym')
  assert.equal(result.message.length > 0, true)
})

test('erro 400 (dados inválidos) vira aviso geral do formulário, não erro de campo', () => {
  const result = mapCreateInstitutionError({ status: 400, message: 'UF inválida' })

  assert.equal(result.field, 'root')
  assert.equal(result.message, 'UF inválida')
})

test('erro desconhecido (rede, etc.) vira aviso geral com mensagem padrão', () => {
  const result = mapCreateInstitutionError(new Error('boom'))

  assert.equal(result.field, 'root')
  assert.equal(result.message.length > 0, true)
})
