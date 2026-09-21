import assert from 'node:assert/strict'
import { afterEach, describe, mock, test } from 'node:test'
import { api } from './client.ts'
import { createInstitution, listInstitutions } from './institutions.ts'

describe('institutions api', () => {
  afterEach(() => {
    mock.restoreAll()
  })

  // @spec:AC-001 Instituição criada com dados válidos
  test('createInstitution envia instituição e laboratório vinculado via POST em /institutions', async () => {
    const mockPost = mock.method(api, 'post', async () => ({
      data: {
        id: '550e8400-e29b-41d4-a716-446655440000',
        name: 'Universidade Federal do Pará',
        acronym: 'UFPA',
        city: 'Belém',
        state: 'PA',
        active: true,
        createdAt: '2026-09-18T10:00:00Z',
      },
    }))

    const payload = {
      name: 'Universidade Federal do Pará',
      acronym: 'UFPA',
      city: 'Belém',
      state: 'PA',
      laboratory: { name: 'LABCOMP-01' },
    }
    const result = await createInstitution(payload)

    assert.equal(mockPost.mock.callCount(), 1)
    assert.equal(mockPost.mock.calls[0].arguments[0], '/institutions')
    assert.deepEqual(mockPost.mock.calls[0].arguments[1], payload)
    assert.equal(result.acronym, 'UFPA')
  })

  test('listInstitutions faz GET em /institutions', async () => {
    const mockGet = mock.method(api, 'get', async () => ({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    }))

    const result = await listInstitutions()

    assert.equal(mockGet.mock.callCount(), 1)
    assert.equal(mockGet.mock.calls[0].arguments[0], '/institutions')
    assert.deepEqual(result.content, [])
  })
})
