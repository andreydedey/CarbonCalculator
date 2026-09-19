import assert from 'node:assert/strict'
import { test } from 'node:test'
import { resolveLayoutView } from './resolveLayoutView.ts'

test('@spec:AC-009 requisição sem identificação de instituição é recusada — layout fica bloqueado sem instituição ativa', () => {
  assert.equal(resolveLayoutView(false), 'blocked')
})

test('@spec:AC-009 requisição sem identificação de instituição é recusada — layout libera conteúdo só com instituição ativa', () => {
  assert.equal(resolveLayoutView(true), 'content')
})
