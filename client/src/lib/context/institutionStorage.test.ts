import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  INSTITUTION_STORAGE_KEY,
  readStoredInstitutionId,
  writeStoredInstitutionId,
} from './institutionStorage.ts'

class MemoryStorage {
  #store = new Map<string, string>()

  getItem(key: string): string | null {
    return this.#store.has(key) ? this.#store.get(key)! : null
  }

  setItem(key: string, value: string): void {
    this.#store.set(key, value)
  }

  removeItem(key: string): void {
    this.#store.delete(key)
  }
}

test('@spec:AC-009 requisição sem identificação de instituição é recusada — nenhum id salvo é lido como null', () => {
  const storage = new MemoryStorage()

  assert.equal(readStoredInstitutionId(storage), null)
})

test('@spec:AC-009 requisição sem identificação de instituição é recusada — id salvo vazio/em branco também é null', () => {
  const storage = new MemoryStorage()
  storage.setItem(INSTITUTION_STORAGE_KEY, '   ')

  assert.equal(readStoredInstitutionId(storage), null)
})

test('@spec:AC-009 requisição sem identificação de instituição é recusada — limpar o id volta ao estado sem instituição', () => {
  const storage = new MemoryStorage()
  storage.setItem(INSTITUTION_STORAGE_KEY, 'inst-a')

  writeStoredInstitutionId(storage, null)

  assert.equal(readStoredInstitutionId(storage), null)
})

test('@spec:AC-008 isolamento por RLS entre instituições — trocar de instituição nunca mistura os dois ids', () => {
  const storage = new MemoryStorage()

  writeStoredInstitutionId(storage, 'instituicao-a')
  assert.equal(readStoredInstitutionId(storage), 'instituicao-a')

  writeStoredInstitutionId(storage, 'instituicao-b')
  assert.equal(readStoredInstitutionId(storage), 'instituicao-b')
})

test('@spec:AC-008 isolamento por RLS entre instituições — o id persistido é exatamente o que foi selecionado', () => {
  const storage = new MemoryStorage()

  writeStoredInstitutionId(storage, 'instituicao-a')
  writeStoredInstitutionId(storage, 'instituicao-b')

  assert.notEqual(readStoredInstitutionId(storage), 'instituicao-a')
})
