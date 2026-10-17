import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { formatEmissionKg } from './format-emission.ts'

describe('formatEmissionKg', () => {
  test('mostra três casas decimais com vírgula', () => {
    assert.equal(formatEmissionKg(1.242), '1,242')
  })

  test('completa com zeros e arredonda na terceira casa', () => {
    assert.equal(formatEmissionKg(3.2), '3,200')
    assert.equal(formatEmissionKg(0.0456), '0,046')
    assert.equal(formatEmissionKg(0), '0,000')
  })

  test('separa milhares com ponto', () => {
    assert.equal(formatEmissionKg(1242.5), '1.242,500')
  })
})
