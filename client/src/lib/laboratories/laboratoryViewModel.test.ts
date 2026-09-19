import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildListQuery, toViewModel } from './laboratoryViewModel.ts'

test('@spec:AC-006 lista mostra apenas laboratórios ativos por padrão — sem toggle, nenhum parâmetro de inclusão é enviado', () => {
  assert.deepEqual(buildListQuery(false), {})
})

test('@spec:AC-007 laboratórios inativos podem ser incluídos na listagem — com toggle ligado, includeInactive é enviado', () => {
  assert.deepEqual(buildListQuery(true), { includeInactive: true })
})

test('@spec:AC-006 lista mostra apenas laboratórios ativos por padrão — laboratório ativo vira card com rótulo Ativo', () => {
  const viewModel = toViewModel({ id: 'lab-1', name: 'LABCOMP-01', active: true })

  assert.deepEqual(viewModel, {
    id: 'lab-1',
    name: 'LABCOMP-01',
    active: true,
    statusLabel: 'Ativo',
  })
})

test('@spec:AC-007 laboratórios inativos podem ser incluídos na listagem — laboratório inativo vira card com rótulo Inativo', () => {
  const viewModel = toViewModel({ id: 'lab-2', name: 'LABCOMP-02', active: false })

  assert.deepEqual(viewModel, {
    id: 'lab-2',
    name: 'LABCOMP-02',
    active: false,
    statusLabel: 'Inativo',
  })
})
