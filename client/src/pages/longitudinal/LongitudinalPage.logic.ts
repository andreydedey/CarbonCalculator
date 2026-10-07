import type { Granularity, SnapshotAggregateDTO } from '../../lib/api/snapshots.ts'

export type { Granularity, SnapshotAggregateDTO }

export interface KpiCard {
  label: string
  value: number | string
}

export const KPI_GRANULARITY: Granularity = 'monthly'

export const GRANULARITY_OPTIONS: { value: Granularity; label: string }[] = [
  { value: 'daily', label: 'Diária' },
  { value: 'weekly', label: 'Semanal' },
  { value: 'monthly', label: 'Mensal' },
  { value: 'period', label: 'Por Período' },
]

export const EMPTY_STATE_MESSAGE =
  'A série histórica é formada automaticamente a cada dia de aula. Os primeiros dados aparecerão amanhã.'

export function computeKpiCards(monthlySnapshots: SnapshotAggregateDTO[]): KpiCard[] {
  if (monthlySnapshots.length === 0) {
    return [
      { label: 'Emissão Atual', value: 0 },
      { label: 'Média Mensal', value: 0 },
      { label: 'Menor Emissão', value: 0 },
    ]
  }

  const current = monthlySnapshots[monthlySnapshots.length - 1].totalEmissionKg

  const last12 = monthlySnapshots.slice(-12)
  const average = last12.reduce((sum, s) => sum + s.totalEmissionKg, 0) / last12.length

  const min = Math.min(...monthlySnapshots.map((s) => s.totalEmissionKg))

  return [
    { label: 'Emissão Atual', value: current },
    { label: 'Média Mensal', value: average },
    { label: 'Menor Emissão', value: min },
  ]
}

export function getVariationColor(variationPct: number | null): string | null {
  if (variationPct === null) return null
  return variationPct > 0 ? 'var(--destructive)' : 'var(--success)'
}
