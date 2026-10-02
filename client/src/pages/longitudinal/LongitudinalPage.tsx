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
import { listSnapshots } from '@/lib/api/snapshots.ts'
import { useState } from 'react'
import {
  computeKpiCards,
  EMPTY_STATE_MESSAGE,
  getVariationColor,
  GRANULARITY_OPTIONS,
  KPI_GRANULARITY,
  type Granularity,
  type KpiCard,
  type SnapshotAggregateDTO,
} from './LongitudinalPage.logic.ts'

// Re-export pure logic so tests can import from this module (consumed by LongitudinalPage.test.ts)
export type { Granularity, KpiCard, SnapshotAggregateDTO }
export {
  computeKpiCards,
  EMPTY_STATE_MESSAGE,
  getVariationColor,
  GRANULARITY_OPTIONS,
  KPI_GRANULARITY,
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
