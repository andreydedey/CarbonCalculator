import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  computeKpiCards,
  EMPTY_STATE_MESSAGE,
  getVariationColor,
  GRANULARITY_OPTIONS,
  KPI_GRANULARITY,
  type SnapshotAggregateDTO,
} from './LongitudinalPage.logic.ts'

/**
 * Este projeto não tem @testing-library/react nem jsdom instalados, e o runner de
 * testes do frontend (`node --test`) não transpila JSX. Por isso — e seguindo a
 * mesma convenção já usada em `computeEmissionFactorRows` (feature fatores-de-emissao,
 * client/src/lib/utils/emission-factor-rows.ts) — este teste exercita a lógica pura
 * extraída para `LongitudinalPage.logic.ts`, em vez de renderizar o DOM.
 */

function makeSnapshot(
  label: string,
  totalEmissionKg: number,
  overrides: Partial<SnapshotAggregateDTO> = {},
): SnapshotAggregateDTO {
  return {
    label,
    startDate: '2025-01-01',
    endDate: '2025-01-31',
    totalEmissionKg,
    totalEnergyKwh: totalEmissionKg / 0.1,
    schoolDays: 20,
    stationCount: 30,
    avgEmissionFactor: 0.1,
    variationPct: null,
    ...overrides,
  }
}

describe('LongitudinalPage', () => {
  describe('@spec:AC-118 KPI cards com valores calculados', () => {
    test('@spec:AC-118 retorna 4 cards na ordem do design, com Emissão Atual, Média Mensal e Menor Emissão calculados', () => {
      const monthlySnapshots = [
        makeSnapshot('Jul 2025', 1000),
        makeSnapshot('Ago 2025', 900),
        makeSnapshot('Set 2025', 1100),
        makeSnapshot('Out 2025', 950),
      ]

      const cards = computeKpiCards(monthlySnapshots)

      assert.equal(cards.length, 4, 'deve haver exatamente 4 KPI cards')
      assert.deepEqual(
        cards.map((c) => c.label),
        ['Emissão Atual', 'Média Mensal', 'Menor Emissão', 'Redução Possível'],
      )

      const [current, average, min, reduction] = cards

      // Emissão Atual = total do mês mais recente (último da série)
      assert.equal(current.value, 950)

      // Média Mensal = média dos totais mensais (aqui, últimos 12 meses == todos, só há 4)
      assert.equal(average.value, (1000 + 900 + 1100 + 950) / 4)

      // Menor Emissão = mínimo mensal histórico
      assert.equal(min.value, 900)

      // Redução Possível = placeholder "—" (depende de PRD 07, fora de escopo em V1)
      assert.equal(reduction.value, '—')
    })

    test('@spec:AC-118 Média Mensal considera apenas os últimos 12 meses quando há mais histórico', () => {
      const olderOutlier = makeSnapshot('Jan 2024', 10000)
      const last12 = Array.from({ length: 12 }, (_, i) => makeSnapshot(`M${i}`, 100))
      const monthlySnapshots = [olderOutlier, ...last12]

      const cards = computeKpiCards(monthlySnapshots)
      const average = cards.find((c) => c.label === 'Média Mensal')

      assert.ok(average, 'deve existir o card Média Mensal')
      assert.equal(average?.value, 100, 'o outlier fora dos últimos 12 meses não deve afetar a média')
    })
  })

  describe('@spec:AC-119 Toggle de granularidade', () => {
    test('@spec:AC-119 expõe as 4 opções de granularidade na ordem do design', () => {
      assert.deepEqual(
        GRANULARITY_OPTIONS.map((o) => o.value),
        ['daily', 'weekly', 'monthly', 'period'],
      )
      assert.deepEqual(
        GRANULARITY_OPTIONS.map((o) => o.label),
        ['Diária', 'Semanal', 'Mensal', 'Por Período'],
      )
    })

    test('@spec:AC-119 KPI cards sempre usam granularidade mensal, independente do toggle selecionado (ASM-034)', () => {
      assert.equal(KPI_GRANULARITY, 'monthly')

      // computeKpiCards não aceita granularidade como parâmetro: o chamador sempre
      // precisa fornecer os snapshots mensais, não os da granularidade selecionada no
      // toggle — isso garante que mudar o toggle não altera os KPI cards.
      assert.equal(computeKpiCards.length, 1)
    })
  })

  describe('@spec:AC-120 Estado vazio', () => {
    test('@spec:AC-120 mensagem explica a coleta automática e não menciona tabela/gráfico vazios', () => {
      assert.equal(
        EMPTY_STATE_MESSAGE,
        'A série histórica é formada automaticamente a cada dia de aula. Os primeiros dados aparecerão amanhã.',
      )
      assert.ok(EMPTY_STATE_MESSAGE.length > 0)
    })
  })

  describe('@spec:AC-121 Cor da variação', () => {
    test('@spec:AC-121 variação positiva (aumento) é vermelha (#DC2626)', () => {
      assert.equal(getVariationColor(12.0), '#DC2626')
      assert.equal(getVariationColor(0.1), '#DC2626')
    })

    test('@spec:AC-121 variação negativa (redução) é verde (#16A34A)', () => {
      assert.equal(getVariationColor(-8.5), '#16A34A')
      assert.equal(getVariationColor(-0.1), '#16A34A')
    })

    test('@spec:AC-121 primeiro registro da série (variationPct null) não é vermelho nem verde', () => {
      const color = getVariationColor(null)
      assert.notEqual(color, '#DC2626')
      assert.notEqual(color, '#16A34A')
    })
  })
})
