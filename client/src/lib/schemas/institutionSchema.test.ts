import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  BRAZILIAN_STATES,
  institutionFormSchema,
  normalizeInstitutionForm,
} from './institutionSchema.ts'

function validInput(overrides: Partial<Record<string, string>> = {}) {
  return {
    name: 'Universidade Federal do Pará',
    acronym: 'UFPA',
    city: 'Belém',
    state: 'PA',
    laboratoryName: 'LABCOMP-01',
    ...overrides,
  }
}

test('@spec:AC-001 Instituição criada com dados válidos — nome, sigla, cidade, UF e laboratório válidos não geram erros', () => {
  const result = institutionFormSchema.safeParse(validInput())

  assert.equal(result.success, true)
})

test('@spec:AC-001 Instituição criada com dados válidos — payload normalizado casa com o corpo esperado por createInstitution', () => {
  const parsed = institutionFormSchema.parse(
    validInput({ name: '  Universidade Federal do Pará  ', acronym: ' UFPA ', city: ' Belém ' }),
  )
  const payload = normalizeInstitutionForm(parsed)

  assert.deepEqual(payload, {
    name: 'Universidade Federal do Pará',
    acronym: 'UFPA',
    city: 'Belém',
    state: 'PA',
    laboratory: { name: 'LABCOMP-01' },
  })
})

test('@spec:AC-001 Instituição criada com dados válidos — cidade em branco vira undefined (campo opcional)', () => {
  const parsed = institutionFormSchema.parse(validInput({ city: '   ' }))
  const payload = normalizeInstitutionForm(parsed)

  assert.equal(payload.city, undefined)
})

test('@spec:AC-003 UF inválida é rejeitada — UF fora da lista das 27 unidades federativas gera erro no campo state', () => {
  const result = institutionFormSchema.safeParse(validInput({ state: 'XX' }))

  assert.equal(result.success, false)
  if (!result.success) {
    const stateError = result.error.issues.find((i) => i.path.includes('state'))
    assert.ok(stateError)
    assert.equal(stateError.message, 'UF inválida. Selecione uma das 27 unidades federativas.')
  }
})

test('@spec:AC-003 UF inválida é rejeitada — todas as 27 UFs oficiais são aceitas', () => {
  assert.equal(BRAZILIAN_STATES.length, 27)
  for (const state of BRAZILIAN_STATES) {
    const result = institutionFormSchema.safeParse(validInput({ state }))
    assert.equal(result.success, true, `UF ${state} deveria ser aceita`)
  }
})

test('@spec:AC-003 UF inválida é rejeitada — string vazia não é uma UF válida', () => {
  const result = institutionFormSchema.safeParse(validInput({ state: '' }))

  assert.equal(result.success, false)
})
