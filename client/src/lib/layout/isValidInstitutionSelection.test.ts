import assert from 'node:assert/strict'
import { test } from 'node:test'
import { isValidInstitutionSelection } from './isValidInstitutionSelection.ts'

test('@spec:AC-008 isolamento por RLS entre instituições — só aceita trocar para instituição realmente listada', () => {
  const options = [
    { id: 'instituicao-a', name: 'Instituição A' },
    { id: 'instituicao-b', name: 'Instituição B' },
  ]

  assert.equal(isValidInstitutionSelection('instituicao-a', options), true)
  assert.equal(isValidInstitutionSelection('instituicao-de-outro-usuario', options), false)
})

test('@spec:AC-008 isolamento por RLS entre instituições — sem opções conhecidas, nenhuma seleção é aceita', () => {
  assert.equal(isValidInstitutionSelection('qualquer-id', []), false)
})
