import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { CalendarDays } from 'lucide-react'
import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { listAcademicPeriods } from '@/lib/api/academic-periods.ts'
import { listSnapshotHistory, listSnapshots } from '@/lib/api/snapshots.ts'
import {
  computeKpiCards,
  EMPTY_STATE_MESSAGE,
  GRANULARITY_OPTIONS,
  type Granularity,
  getVariationColor,
  KPI_GRANULARITY,
  type KpiCard,
  type SnapshotAggregateDTO,
} from './LongitudinalPage.logic.ts'

export type { Granularity, KpiCard, SnapshotAggregateDTO }
export {
  computeKpiCards,
  EMPTY_STATE_MESSAGE,
  GRANULARITY_OPTIONS,
  getVariationColor,
  KPI_GRANULARITY,
}

export function LongitudinalPage() {
  const [granularity, setGranularity] = useState<Granularity>('monthly')
  const [selectedPeriodId, setSelectedPeriodId] = useState<string>('all')

  const { data: periodsPage } = useQuery({
    queryKey: ['academic-periods-all'],
    queryFn: () => listAcademicPeriods(0, 50),
  })
  const periods = periodsPage?.content ?? []

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId)
  const dateFilter = selectedPeriod
    ? { startDate: selectedPeriod.startDate, endDate: selectedPeriod.endDate }
    : {}

  const { data: chartData = [], isLoading: isChartLoading } = useQuery({
    queryKey: ['snapshots', granularity, selectedPeriodId],
    queryFn: () => listSnapshots({ granularity, ...dateFilter }),
  })

  const { data: monthlyData = [] } = useQuery({
    queryKey: ['snapshots', KPI_GRANULARITY, selectedPeriodId],
    queryFn: () => listSnapshots({ granularity: KPI_GRANULARITY, ...dateFilter }),
  })

  const {
    data: historyData,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['snapshots-history', granularity, selectedPeriodId],
    queryFn: ({ pageParam }) =>
      listSnapshotHistory({ granularity, page: pageParam, ...dateFilter }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  })
  const historyRows = historyData?.pages.flatMap((p) => p.content) ?? []

  const kpiCards = computeKpiCards(monthlyData)
  const isEmpty = !isChartLoading && chartData.length === 0 && monthlyData.length === 0

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <p className="text-xs font-normal text-muted-foreground">Análise &rsaquo; Acompanhamento Longitudinal</p>
        <h1 className="font-heading text-2xl font-bold">Acompanhamento Longitudinal</h1>
      </div>

      <div className="grid grid-cols-3 gap-4">
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

      <div className="flex items-center gap-1 rounded-lg border bg-muted/30 p-1 w-fit">
        {GRANULARITY_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => setGranularity(opt.value)}
            className={`rounded-md px-4 py-1.5 text-sm font-medium transition-colors ${
              granularity === opt.value
                ? 'bg-foreground text-background'
                : 'text-muted-foreground hover:bg-muted'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {isEmpty ? (
        <div className="rounded-lg border p-8 flex items-center justify-center">
          <p className="text-sm text-muted-foreground text-center max-w-sm">{EMPTY_STATE_MESSAGE}</p>
        </div>
      ) : (
        <>
          <div className="rounded-lg border p-4">
            <div className="flex items-start justify-between mb-4">
              <div>
                <p className="text-sm font-medium">Evolução de Emissões</p>
                <p className="text-xs text-muted-foreground mt-0.5">kg CO₂ emitidos · visão {granularity === 'monthly' ? 'mensal' : granularity === 'weekly' ? 'semanal' : granularity === 'daily' ? 'diária' : 'por período'}</p>
              </div>
              {periods.length > 0 && (
                <Select value={selectedPeriodId} onValueChange={setSelectedPeriodId}>
                  <SelectTrigger className="w-auto h-8 text-xs gap-1.5 border-dashed">
                    <CalendarDays className="size-3 text-primary" />
                    <SelectValue placeholder="Todos os períodos" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">Todos os períodos</SelectItem>
                    {periods.map((p) => (
                      <SelectItem key={p.id} value={p.id}>{p.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
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
                <Bar dataKey="totalEmissionKg" fill="var(--chart-1)" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>

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
                {historyRows.map((row) => {
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
            <div className="p-2">
              <LoadMoreButton
                fetchNextPage={fetchNextPage}
                hasNextPage={hasNextPage}
                isFetchingNextPage={isFetchingNextPage}
              />
            </div>
          </div>
        </>
      )}
    </div>
  )
}
