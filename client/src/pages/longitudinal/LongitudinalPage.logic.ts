// Granularity is defined here (not imported from @/lib/api/snapshots.ts) so that
// node --test can run this file without the @/ path alias resolver.
export type Granularity = 'daily' | 'weekly' | 'monthly' | 'period'

export interface SnapshotAggregateDTO {
  label: string
  startDate: string
  endDate: string
  periodId?: string
  totalEmissionKg: number
  totalEnergyKwh: number
  schoolDays: number
  stationCount: number
  avgEmissionFactor: number
  variationPct: number | null
}

export interface KpiCard {
  label: string
  value: number | string
}

// KPI cards always use monthly granularity, independent of the toggle (ASM-034, AC-119)
export const KPI_GRANULARITY: Granularity = 'monthly'

// Toggle options in design order (AC-119)
export const GRANULARITY_OPTIONS: { value: Granularity; label: string }[] = [
  { value: 'daily', label: 'Diária' },
  { value: 'weekly', label: 'Semanal' },
  { value: 'monthly', label: 'Mensal' },
  { value: 'period', label: 'Por Período' },
]

// AC-120: empty-state message
export const EMPTY_STATE_MESSAGE =
  'A série histórica é formada automaticamente a cada dia de aula. Os primeiros dados aparecerão amanhã.'

// AC-118: 3 KPI cards — Emissão Atual, Média Mensal (últimos 12 meses), Menor Emissão
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

// AC-121: positive variation (increase) = red, negative (reduction) = green, null = no color
export function getVariationColor(variationPct: number | null): string | null {
  if (variationPct === null) return null
  return variationPct > 0 ? '#DC2626' : '#16A34A'
}
