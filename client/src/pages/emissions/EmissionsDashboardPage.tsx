import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Calculator, CircleCheck, Loader2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { EquivalenceCards } from '@/components/emissions/EquivalenceCards'
import { ExportButton } from '@/components/emissions/ExportButton'
import { ReadinessCheck } from '@/components/emissions/ReadinessCheck'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { listAcademicPeriods } from '@/lib/api/academic-periods'
import { type EmissionResult, getEmissions, getReadiness } from '@/lib/api/emissions'

function MethodologyCard() {
  return (
    <div className="rounded-[10px] border border-border bg-card p-6 flex flex-col gap-5">
      <div className="flex items-center justify-between border-b border-border pb-4">
        <div className="flex flex-col gap-1">
          <h2 className="text-base font-semibold">Metodologia de Cálculo</h2>
          <p className="text-[13px] text-muted-foreground">
            Baseado no Programa Brasileiro GHG Protocol — Escopo 2 (energia elétrica)
          </p>
        </div>
        <div className="flex items-center gap-1.5 rounded-md bg-accent px-3.5 py-1.5">
          <CircleCheck className="size-3.5 text-primary" />
          <span className="text-xs font-medium text-primary">MCTI/SIRENE 2024</span>
        </div>
      </div>
      <div className="rounded-lg bg-muted p-5 flex flex-col gap-3">
        <span className="text-xs font-medium text-muted-foreground">Fórmula aplicada</span>
        <span className="font-mono text-base font-semibold">
          E = Σ (P_equip × h_uso × d_letivos) × FE_sin
        </span>
        <div className="flex gap-5">
          {[
            ['E', '= Emissão (kg CO₂)'],
            ['P_equip', '= Potência (kW)'],
            ['h_uso', '= Horas/dia'],
            ['d_letivos', '= Dias letivos'],
            ['FE_sin', '= Fator emissão'],
          ].map(([sym, desc]) => (
            <div key={sym} className="flex items-center gap-1">
              <span className="font-mono text-[11px] font-semibold text-primary">{sym}</span>
              <span className="text-[11px] text-muted-foreground">{desc}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function MetricCard({
  label,
  value,
  subtitle,
  highlighted,
}: {
  label: string
  value: string
  subtitle: string
  highlighted?: boolean
}) {
  return (
    <div
      className={`flex-1 rounded-[10px] border border-border p-5 flex flex-col gap-2 ${highlighted ? 'bg-accent' : 'bg-card'}`}
    >
      <span className="text-xs font-medium tracking-wider text-muted-foreground">{label}</span>
      <span className="font-mono text-[22px] font-semibold">{value}</span>
      <span className="text-[11px] text-muted-foreground">{subtitle}</span>
    </div>
  )
}

function ResultMetrics({
  result,
  totalSchoolDays,
  periodName,
}: {
  result: EmissionResult
  totalSchoolDays: number
  periodName: string
}) {
  const avgFactor =
    result.inputs.emissionFactors.length > 0
      ? result.inputs.emissionFactors.reduce((s, f) => s + f.value, 0) /
        result.inputs.emissionFactors.length
      : null

  const TARIFF = 0.7
  const estimatedCost = result.totalEnergyKwh * TARIFF

  return (
    <div className="flex gap-4">
      <MetricCard
        highlighted
        label="EMISSÃO TOTAL SEMESTRE"
        value={`${formatNumber(result.totalEmissionKg)} kg CO₂`}
        subtitle={`${periodName} · ${totalSchoolDays} dias letivos`}
      />
      <MetricCard
        label="CONSUMO ESTIMADO"
        value={`${formatNumber(result.totalEnergyKwh)} kWh`}
        subtitle="energia elétrica total"
      />
      <MetricCard
        label="FATOR DE EMISSÃO SIN"
        value={avgFactor != null ? avgFactor.toFixed(4).replace('.', ',') : '—'}
        subtitle={`tCO₂/MWh · Média ${new Date().getFullYear()}`}
      />
      <MetricCard
        label="CUSTO ESTIMADO"
        value={`R$ ${formatNumber(estimatedCost)}`}
        subtitle={`tarifa média R$ ${TARIFF.toFixed(2).replace('.', ',')}/kWh`}
      />
    </div>
  )
}

function LabDetailTable({ result }: { result: EmissionResult }) {
  const totalSchoolDays = result.byMonth.reduce((s, m) => s + m.schoolDays, 0)

  const labRows = result.byLaboratory.map((lab) => {
    const totalWatts = lab.configurations.reduce((s, c) => s + c.consumptionWatts * c.quantity, 0)
    const hoursPerDay =
      totalWatts > 0 && totalSchoolDays > 0
        ? (lab.energyKwh * 1000) / (totalWatts * totalSchoolDays)
        : null
    const pct = result.totalEmissionKg > 0 ? (lab.emissionKg / result.totalEmissionKg) * 100 : 0

    return { lab, totalWatts, hoursPerDay, pct }
  })

  const totals = {
    equips: labRows.reduce((s, r) => s + r.lab.stationCount, 0),
    watts: labRows.reduce((s, r) => s + r.totalWatts, 0),
    kwh: result.totalEnergyKwh,
    emission: result.totalEmissionKg,
  }

  const colWidths = 'grid-cols-[160px_160px_160px_130px_160px_170px_1fr]'

  return (
    <div className="rounded-[10px] border border-border bg-card overflow-hidden">
      <div className="flex flex-col gap-1 px-6 py-5 border-b border-border">
        <h2 className="text-base font-semibold">Detalhamento por Laboratório</h2>
        <p className="text-[13px] text-muted-foreground">
          Decomposição do cálculo de emissões por laboratório e tipo de equipamento
        </p>
      </div>

      {/* Header */}
      <div className={`grid ${colWidths} items-center h-11 bg-muted px-6 border-b border-border`}>
        <span className="text-xs font-medium tracking-wider text-muted-foreground">
          LABORATÓRIO
        </span>
        <span className="text-xs font-medium tracking-wider text-muted-foreground">
          EQUIPAMENTOS
        </span>
        <span className="text-xs font-medium tracking-wider text-muted-foreground">
          POTÊNCIA TOTAL
        </span>
        <span className="text-xs font-medium tracking-wider text-muted-foreground">HORAS/DIA</span>
        <span className="text-xs font-medium tracking-wider text-muted-foreground">
          CONSUMO (KWH)
        </span>
        <span className="text-xs font-medium tracking-wider text-muted-foreground">
          EMISSÃO (KG CO₂)
        </span>
        <span className="text-xs font-medium tracking-wider text-muted-foreground text-right">
          % DO TOTAL
        </span>
      </div>

      {/* Rows */}
      {labRows.map((row, i) => (
        <div
          key={row.lab.laboratoryId}
          className={`grid ${colWidths} items-center h-[52px] px-6 border-b border-border ${i % 2 === 1 ? 'bg-[#fbfcf9]' : 'bg-card'}`}
        >
          <span className="text-[13px] font-semibold">{row.lab.laboratoryName}</span>
          <span className="font-mono text-[13px]">{row.lab.stationCount}</span>
          <span className="font-mono text-xs text-muted-foreground">
            {formatNumber(row.totalWatts)} W
          </span>
          <span className="font-mono text-[13px]">
            {row.hoursPerDay != null ? row.hoursPerDay.toFixed(1).replace('.', ',') : '—'}
          </span>
          <span className="font-mono text-[13px]">{formatNumber(row.lab.energyKwh)}</span>
          <span className="font-mono text-[13px] font-semibold">
            {formatNumber(row.lab.emissionKg)}
          </span>
          <span className="text-[13px] font-semibold text-primary text-right">
            {row.pct.toFixed(1).replace('.', ',')}%
          </span>
        </div>
      ))}

      {/* Footer */}
      <div className={`grid ${colWidths} items-center h-12 bg-muted px-6`}>
        <span className="text-[13px] font-bold">Total</span>
        <span className="font-mono text-[13px] font-semibold">{totals.equips}</span>
        <span className="font-mono text-[13px] font-semibold">{formatNumber(totals.watts)} W</span>
        <span className="text-[13px] text-muted">—</span>
        <span className="font-mono text-[13px] font-semibold">{formatNumber(totals.kwh)}</span>
        <span className="font-mono text-[13px] font-bold">{formatNumber(totals.emission)}</span>
        <span className="text-[13px] font-bold text-primary text-right">100%</span>
      </div>
    </div>
  )
}

function formatNumber(n: number): string {
  return Math.round(n).toLocaleString('pt-BR')
}

export function EmissionsDashboardPage() {
  const queryClient = useQueryClient()
  const [selectedPeriodId, setSelectedPeriodId] = useState<string | null>(null)
  const [recalculating, setRecalculating] = useState(false)

  const { data: periodsPage } = useQuery({
    queryKey: ['academic-periods', 'all-for-emissions'],
    queryFn: () => listAcademicPeriods(0, 100),
  })

  const periods = periodsPage?.content ?? []

  const resolvedPeriodId = useMemo(() => {
    if (selectedPeriodId && periods.some((p) => p.id === selectedPeriodId)) return selectedPeriodId
    return periods[0]?.id ?? null
  }, [periods, selectedPeriodId])

  const { data: readiness, isLoading: readinessLoading } = useQuery({
    queryKey: ['emissions-readiness', resolvedPeriodId],
    queryFn: () => getReadiness(resolvedPeriodId as string),
    enabled: !!resolvedPeriodId,
  })

  const { data: result, isLoading: resultLoading } = useQuery({
    queryKey: ['emissions', resolvedPeriodId],
    queryFn: () => getEmissions(resolvedPeriodId as string),
    enabled: !!resolvedPeriodId && readiness?.ready === true,
  })

  const selectedPeriod = periods.find((p) => p.id === resolvedPeriodId)

  const totalSchoolDays = result?.byMonth.reduce((s, m) => s + m.schoolDays, 0) ?? 0

  async function handleRecalculate() {
    if (!resolvedPeriodId) return
    setRecalculating(true)
    await queryClient.invalidateQueries({ queryKey: ['emissions', resolvedPeriodId] })
    setRecalculating(false)
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs text-muted-foreground">Análise &rsaquo; Cálculo de Emissões</p>
          <h1 className="font-heading text-2xl font-bold">
            Cálculo de Emissões{selectedPeriod ? ` — ${selectedPeriod.name}` : ''}
          </h1>
        </div>
        <div className="flex items-center gap-2.5">
          {periods.length > 0 && (
            <Select value={resolvedPeriodId ?? undefined} onValueChange={setSelectedPeriodId}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="Selecione um período" />
              </SelectTrigger>
              <SelectContent>
                {periods.map((p) => (
                  <SelectItem key={p.id} value={p.id}>
                    {p.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
          {result && selectedPeriod && (
            <ExportButton periodId={selectedPeriod.id} periodName={selectedPeriod.name} />
          )}
          <Button onClick={handleRecalculate} disabled={!resolvedPeriodId || recalculating}>
            {recalculating ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Calculator className="size-4" />
            )}
            Recalcular
          </Button>
        </div>
      </div>

      {periods.length === 0 ? (
        <p className="text-sm text-muted-foreground italic">
          Nenhum período letivo cadastrado. Cadastre um período para calcular emissões.
        </p>
      ) : readinessLoading ? (
        <p className="text-sm text-muted-foreground">Verificando pré-requisitos...</p>
      ) : (
        <>
          {readiness && <ReadinessCheck readiness={readiness} />}

          {readiness && !readiness.ready ? (
            <p className="text-sm text-muted-foreground italic">
              Corrija os pré-requisitos acima para calcular as emissões deste período.
            </p>
          ) : resultLoading ? (
            <p className="text-sm text-muted-foreground">Calculando emissões...</p>
          ) : result ? (
            <div className="flex flex-col gap-6">
              <MethodologyCard />

              <ResultMetrics
                result={result}
                totalSchoolDays={totalSchoolDays}
                periodName={selectedPeriod?.name ?? ''}
              />

              <LabDetailTable result={result} />

              <EquivalenceCards
                carKm={result.equivalences.carKm}
                treesNeeded={result.equivalences.treesNeeded}
                totalEnergyKwh={result.totalEnergyKwh}
              />
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}
