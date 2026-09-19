import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildLaboratoryPayload, resolveLaboratoryFormMode } from './buildLaboratoryPayload.ts'

test('@spec:AC-004 laboratório criado com nome — payload traz o nome sem espaços nas pontas', () => {
  const payload = buildLaboratoryPayload({ name: '  LABCOMP-02  ' })

  assert.deepEqual(payload, { name: 'LABCOMP-02' })
})

test('@spec:AC-004 laboratório criado com nome — modo é "create" quando não há laboratório carregado', () => {
  assert.equal(resolveLaboratoryFormMode(undefined), 'create')
})

test('@spec:AC-004 laboratório criado com nome — modo é "edit" quando há um laboratório carregado', () => {
  assert.equal(resolveLaboratoryFormMode({ id: 'lab-1', name: 'LABCOMP-01', active: true }), 'edit')
})
