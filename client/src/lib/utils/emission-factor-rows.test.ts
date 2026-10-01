import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import { computeEmissionFactorRows, type EmissionFactor } from './emission-factor-rows.ts'

function makeFactor(referenceMonth: string, value = 0.05): EmissionFactor {
  return {
    id: `id-${referenceMonth}`,
    referenceMonth,
    value,
    source: 'MCTI — SIN',
    createdAt: '2025-01-01T00:00:00Z',
  }
}

describe('computeEmissionFactorRows', () => {
  // @spec:AC-116 Meses sem fator exibidos como "Pendente"
  test('@spec:AC-116 marca como pendente meses já passados sem fator cadastrado', () => {
    const currentDate = new Date('2025-06-15T00:00:00Z')
    const factors = [makeFactor('2025-01'), makeFactor('2025-03')]

    const rows = computeEmissionFactorRows(factors, currentDate, 2025)

    const pendingMonths = rows.filter((r) => r.status === 'pendente').map((r) => r.referenceMonth)
    assert.deepEqual(pendingMonths.sort(), ['2025-02', '2025-04', '2025-05', '2025-06'])

    // Meses futuros (ainda não alcançados pela data atual) não entram como pendente
    assert.ok(!pendingMonths.includes('2025-07'))
    assert.ok(!pendingMonths.includes('2025-12'))

    for (const row of rows) {
      if (row.status === 'pendente') {
        assert.equal(row.factor, null)
      }
    }
  })

  // @spec:AC-117 Fator mais recente marcado como "Em uso"
  test('@spec:AC-117 marca exatamente um fator — o mais recente ≤ hoje — como em-uso', () => {
    const currentDate = new Date('2025-06-15T00:00:00Z')
    const factors = [makeFactor('2025-01'), makeFactor('2025-03'), makeFactor('2025-05')]

    const rows = computeEmissionFactorRows(factors, currentDate, 2025)

    const emUsoRows = rows.filter((r) => r.status === 'em-uso')
    assert.equal(emUsoRows.length, 1, 'deve haver exatamente um registro em-uso')
    assert.equal(emUsoRows[0].referenceMonth, '2025-05')
  })

  // @spec:AC-118 Fatores anteriores ao "Em uso" marcados como "Anterior"
  test('@spec:AC-118 marca fatores mais antigos que o em-uso como anterior', () => {
    const currentDate = new Date('2025-06-15T00:00:00Z')
    const factors = [makeFactor('2025-01'), makeFactor('2025-03'), makeFactor('2025-05')]

    const rows = computeEmissionFactorRows(factors, currentDate, 2025)

    const anteriorMonths = rows
      .filter((r) => r.status === 'anterior')
      .map((r) => r.referenceMonth)
      .sort()
    assert.deepEqual(anteriorMonths, ['2025-01', '2025-03'])
  })
})
