import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { listSnapshots, type Granularity } from '@/lib/api/snapshots.ts'
import { useState } from 'react'

// --- Exported types and constants (consumed by LongitudinalPage.test.tsx) ---

export type { Granularity }

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

// AC-118: 4 KPI cards — Emissão Atual, Média Mensal (últimos 12 meses), Menor Emissão, Redução Possível
export function computeKpiCards(monthlySnapshots: SnapshotAggregateDTO[]): KpiCard[] {
  if (monthlySnapshots.length === 0) {
    return [
      { label: 'Emissão Atual', value: 0 },
      { label: 'Média Mensal', value: 0 },
      { label: 'Menor Emissão', value: 0 },
      { label: 'Redução Possível', value: '—' },
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
    { label: 'Redução Possível', value: '—' },
  ]
}

// AC-121: positive variation (increase) = red, negative (reduction) = green, null = no color
export function getVariationColor(variationPct: number | null): string | null {
  if (variationPct === null) return null
  return variationPct > 0 ? '#DC2626' : '#16A34A'
}

// --- Page component ---

export function LongitudinalPage() {
  const [granularity, setGranularity] = useState<Granularity>('monthly')

  const { data: chartData = [], isLoading: isChartLoading } = useQuery({
    queryKey: ['snapshots', granularity],
    queryFn: () => listSnapshots({ granularity }),
  })

  const { data: monthlyData = [] } = useQuery({
    queryKey: ['snapshots', KPI_GRANULARITY],
    queryFn: () => listSnapshots({ granularity: KPI_GRANULARITY }),
  })

  const kpiCards = computeKpiCards(monthlyData)
  const isEmpty = !isChartLoading && chartData.length === 0 && monthlyData.length === 0

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <p className="text-xs font-normal text-muted-foreground">Análise &rsaquo; Acompanhamento Longitudinal</p>
        <div className="flex items-center justify-between">
          <h1 className="font-heading text-2xl font-bold">Acompanhamento Longitudinal</h1>
          <div className="flex items-center gap-2 rounded-lg border bg-muted/30 px-3 py-1.5 text-xs text-muted-foreground">
            <span className="text-base">🕐</span>
            <span>Coleta automática diária</span>
          </div>
        </div>
      </div>

      {/* KPI Cards (AC-118) */}
      <div className="grid grid-cols-4 gap-4">
        {kpiCards.map((card) => (
          <div key={card.label} className="rounded-lg border bg-card p-4 flex flex-col gap-1">
            <p className="text-xs text-muted-foreground uppercase tracking-wide">{card.label}</p>
            <p className="text-2xl font-bold font-mono">
              {typeof card.value === 'number'
                ? card.value.toLocaleString('pt-BR', { maximumFractionDigits: 1 })
                : card.value}
            </p>
            {typeof card.value === 'number' && (
              <p className="text-xs text-muted-foreground">kg CO₂</p>
            )}
          </div>
        ))}
      </div>

      {/* Granularity toggle (AC-119) */}
      <div className="flex items-center gap-1 rounded-lg border bg-muted/30 p-1 w-fit">
        {GRANULARITY_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => setGranularity(opt.value)}
            className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
              granularity === opt.value
                ? 'bg-[#192219] text-white'
                : 'text-muted-foreground hover:bg-muted'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* Empty state (AC-120) */}
      {isEmpty ? (
        <div className="rounded-lg border p-8 flex items-center justify-center">
          <p className="text-sm text-muted-foreground text-center max-w-sm">{EMPTY_STATE_MESSAGE}</p>
        </div>
      ) : (
        <>
          {/* Bar chart */}
          <div className="rounded-lg border p-4">
            <p className="text-sm font-medium mb-1">Evolução de Emissões</p>
            <p className="text-xs text-muted-foreground mb-4">kg CO₂ emitidos · visão {granularity === 'monthly' ? 'mensal' : granularity === 'weekly' ? 'semanal' : granularity === 'daily' ? 'diária' : 'por período'}</p>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={chartData} margin={{ top: 4, right: 8, left: 0, bottom: 4 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip
                  formatter={(value: number) => [
                    `${value.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} kg CO₂`,
                    'Emissão',
                  ]}
                />
                <Bar dataKey="totalEmissionKg" fill="#24744D" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Historical table */}
          <div className="rounded-lg border">
            <div className="px-4 py-3 border-b">
              <p className="text-sm font-medium">Histórico de Emissões</p>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-4">DATA</TableHead>
                  <TableHead className="px-4 text-right">EMISSÃO (kg CO₂)</TableHead>
                  <TableHead className="px-4 text-right">ENERGIA (kWh)</TableHead>
                  <TableHead className="px-4 text-right">DIAS LETIVOS</TableHead>
                  <TableHead className="px-4 text-right">VARIAÇÃO</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...chartData].reverse().map((row) => {
                  const color = getVariationColor(row.variationPct)
                  return (
                    <TableRow key={row.label + row.startDate}>
                      <TableCell className="px-4 font-medium">{row.label}</TableCell>
                      <TableCell className="px-4 text-right font-mono text-xs">
                        {row.totalEmissionKg.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}
                      </TableCell>
                      <TableCell className="px-4 text-right font-mono text-xs">
                        {row.totalEnergyKwh.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}
                      </TableCell>
                      <TableCell className="px-4 text-right font-mono text-xs">
                        {row.schoolDays}
                      </TableCell>
                      <TableCell className="px-4 text-right font-mono text-xs">
                        {row.variationPct === null ? (
                          <span className="text-muted-foreground">—</span>
                        ) : (
                          <span style={{ color: color ?? undefined }}>
                            {row.variationPct > 0 ? '+' : ''}
                            {row.variationPct.toFixed(1)}%
                          </span>
                        )}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        </>
      )}
    </div>
  )
}
