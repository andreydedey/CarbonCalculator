import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { consumptionSourceLabel, isEstimate } from './consumption-source.ts'

describe('consumption source', () => {
  test('@spec:AC-131 mostra a origem do consumo em português e destaca estimativas', () => {
    assert.equal(consumptionSourceLabel('measurement_combined'), 'Medição conjunta')
    assert.equal(
      consumptionSourceLabel('measurement_computer+specification_monitor'),
      'Medição do computador + especificação do monitor',
    )
    assert.equal(
      consumptionSourceLabel('specification_computer+measurement_monitor'),
      'Especificação do computador + medição do monitor',
    )
    assert.equal(consumptionSourceLabel('measurement_computer'), 'Medição do computador')
    assert.equal(consumptionSourceLabel('specification'), 'Especificação')

    assert.equal(isEstimate('specification'), true)
    assert.equal(isEstimate('measurement_computer+specification_monitor'), true)
    assert.equal(isEstimate('measurement_combined'), false)
    assert.equal(isEstimate('measurement_computer+measurement_monitor'), false)
  })
})
