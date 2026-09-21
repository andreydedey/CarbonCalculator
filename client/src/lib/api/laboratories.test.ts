import assert from 'node:assert/strict'
import { afterEach, describe, mock, test } from 'node:test'
import { api, ConflictError } from './client.ts'
import {
  createLaboratory,
  deactivateLaboratory,
  deleteLaboratory,
  listLaboratories,
} from './laboratories.ts'

describe('laboratories api', () => {
  afterEach(() => {
    mock.restoreAll()
  })

  // @spec:AC-006 Lista mostra apenas laboratórios ativos por padrão
  test('listLaboratories não envia params por padrão', async () => {
    const mockGet = mock.method(api, 'get', async () => ({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    }))

    const result = await listLaboratories()

    assert.equal(mockGet.mock.callCount(), 1)
    assert.equal(mockGet.mock.calls[0].arguments[0], '/laboratories')
    assert.equal(mockGet.mock.calls[0].arguments[1]?.params, undefined)
    assert.deepEqual(result.content, [])
  })

  // @spec:AC-007 Laboratórios inativos podem ser incluídos na listagem
  test('listLaboratories envia params { active: false } quando includeInactive é true', async () => {
    const mockGet = mock.method(api, 'get', async () => ({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    }))

    await listLaboratories({ includeInactive: true })

    assert.equal(mockGet.mock.callCount(), 1)
    assert.deepEqual(mockGet.mock.calls[0].arguments[1], { params: { active: false } })
  })

  // @spec:AC-004 Laboratório criado com nome
  test('createLaboratory faz POST em /laboratories com o nome informado', async () => {
    const mockPost = mock.method(api, 'post', async () => ({
      data: { id: '660e8400', name: 'LABCOMP-02', active: true },
    }))

    const result = await createLaboratory({ name: 'LABCOMP-02' })

    assert.equal(mockPost.mock.callCount(), 1)
    assert.equal(mockPost.mock.calls[0].arguments[0], '/laboratories')
    assert.deepEqual(mockPost.mock.calls[0].arguments[1], { name: 'LABCOMP-02' })
    assert.equal(result.name, 'LABCOMP-02')
  })

  // @spec:AC-011 Desativação preserva o laboratório
  test('deactivateLaboratory faz PATCH em /laboratories/{id}/deactivate', async () => {
    const mockPatch = mock.method(api, 'patch', async () => ({
      data: { id: 'lab-1', name: 'LABCOMP-01', active: false },
    }))

    const result = await deactivateLaboratory('lab-1')

    assert.equal(mockPatch.mock.callCount(), 1)
    assert.equal(mockPatch.mock.calls[0].arguments[0], '/laboratories/lab-1/deactivate')
    assert.equal(result.active, false)
  })

  test('@spec:AC-012 exclusão bloqueada quando há dependentes — deleteLaboratory rejeita com ConflictError ao receber 409', async () => {
    mock.method(api, 'delete', async () => {
      throw new ConflictError(
        409,
        'Laboratório possui registros dependentes; desative-o em vez de excluir',
      )
    })

    await assert.rejects(
      () => deleteLaboratory('lab-1'),
      (error: unknown) => {
        assert.ok(error instanceof ConflictError)
        assert.equal((error as ConflictError).status, 409)
        return true
      },
    )
  })

  test('@spec:AC-013 exclusão permitida quando não há dependentes — deleteLaboratory faz DELETE e resolve sem corpo', async () => {
    const mockDelete = mock.method(api, 'delete', async () => ({
      data: '',
      status: 204,
    }))

    const result = await deleteLaboratory('lab-1')

    assert.equal(mockDelete.mock.callCount(), 1)
    assert.equal(mockDelete.mock.calls[0].arguments[0], '/laboratories/lab-1')
    assert.equal(result, undefined)
  })
})
