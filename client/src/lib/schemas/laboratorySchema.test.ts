import assert from 'node:assert/strict'
import { test } from 'node:test'
import { laboratoryFormSchema } from './laboratorySchema.ts'

test('@spec:AC-004 laboratório criado com nome — nome válido é aceito, sem espaços nas pontas', () => {
  const result = laboratoryFormSchema.parse({ name: '  LABCOMP-01  ' })

  assert.equal(result.name, 'LABCOMP-01')
})

test('@spec:AC-005 laboratório sem nome é rejeitado — nome vazio é recusado', () => {
  const result = laboratoryFormSchema.safeParse({ name: '' })

  assert.equal(result.success, false)
})

test('@spec:AC-005 laboratório sem nome é rejeitado — nome só com espaços em branco é recusado', () => {
  const result = laboratoryFormSchema.safeParse({ name: '    ' })

  assert.equal(result.success, false)
})
