import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildDeactivateConfirmationMessage } from './buildDeactivateMessage.ts'

test('@spec:AC-011 desativação preserva o laboratório — mensagem cita o nome do laboratório', () => {
  const message = buildDeactivateConfirmationMessage('LABCOMP-01')

  assert.match(message, /LABCOMP-01/)
})

test('@spec:AC-011 desativação preserva o laboratório — mensagem afirma que os dados são preservados', () => {
  const message = buildDeactivateConfirmationMessage('LABCOMP-01')

  assert.match(message, /preservad/i)
})

test('@spec:AC-011 desativação preserva o laboratório — mensagem não menciona exclusão/remoção permanente', () => {
  const message = buildDeactivateConfirmationMessage('LABCOMP-01').toLowerCase()

  assert.equal(message.includes('exclu'), false)
  assert.equal(message.includes('remov'), false)
  assert.equal(message.includes('apagad'), false)
})
