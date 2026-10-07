import { useQuery } from '@tanstack/react-query'
import { CircleCheck } from 'lucide-react'
import { useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ConsumptionSources } from '@/components/emissions/ConsumptionSources'
import { EquivalenceCards } from '@/components/emissions/EquivalenceCards'
import { ExportButton } from '@/components/emissions/ExportButton'
import { ReadinessCheck } from '@/components/emissions/ReadinessCheck'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
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
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { listAcademicPeriods } from '@/lib/api/academic-periods'
import { type EmissionResult, getEmissions, getReadiness } from '@/lib/api/emissions'
import { formatDate } from '@/lib/utils/occupation'

function MethodologyCard() {
  return (
    <Card className="p-6 gap-5">
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
      <CardContent className="rounded-lg bg-muted p-5 flex flex-col gap-3">
        <span className="text-xs font-medium text-muted-foreground">Fórmula aplicada</span>
        <span className="font-mono text-base font-semibold">
          E = Σ_dias Σ_aulas (P_estação × estações_usadas × duração_aula) × FE_mês
        </span>
        <div className="flex flex-wrap gap-5">
          {[
            ['E', '= Emissão (kg CO₂)'],
            ['P_estação', '= Potência por estação (kW)'],
            ['estações_usadas', '= da grade ou da exceção do dia'],
            ['duração_aula', '= horas'],
            ['FE_mês', '= Fator do mês'],
          ].map(([sym, desc]) => (
            <div key={sym} className="flex items-center gap-1">
              <span className="font-mono text-[11px] font-semibold text-primary">{sym}</span>
              <span className="text-[11px] text-muted-foreground">{desc}</span>
            </div>
          ))}
        </div>
        <span className="text-[11px] text-muted-foreground">
          Estações não usadas são consideradas desligadas.
        </span>
      </CardContent>
    </Card>
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
    <Card className={`flex-1 p-5 gap-2 ${highlighted ? 'bg-accent' : ''}`}>
      <span className="text-xs font-medium tracking-wider text-muted-foreground">{label}</span>
      <span className="font-mono text-[22px] font-semibold">{value}</span>
      <span className="text-[11px] text-muted-foreground">{subtitle}</span>
    </Card>
  )
}

function ResultMetrics({ result }: { result: EmissionResult }) {
  const avgFactor =
    result.emissionFactors.length > 0
      ? result.emissionFactors.reduce((s, f) => s + f.value, 0) / result.emissionFactors.length
      : null

  const TARIFF = 0.7
  const estimatedCost = result.totalEnergyKwh * TARIFF
  const progress =
    result.schoolDaysTotal > 0 ? (result.schoolDaysElapsed / result.schoolDaysTotal) * 100 : 0
  const until = result.realizedUntil ? formatDate(result.realizedUntil) : null

  return (
    <div className="flex flex-col gap-4">
      <div className="flex gap-4">
        <Card className="flex-[2] gap-2 bg-accent p-5">
          <span className="text-xs font-medium tracking-wider text-muted-foreground">
            EMISSÃO REALIZADA
          </span>
          <span className="font-mono text-[26px] font-semibold">
            {formatNumber(result.totalEmissionKg)} kg CO₂
          </span>
          <span className="text-[11px] text-muted-foreground">
            {until
              ? `até ${until} · ${result.schoolDaysElapsed} de ${result.schoolDaysTotal} dias letivos`
              : 'o período ainda não começou'}
          </span>
          <Progress value={progress} className="h-1.5" />
        </Card>
        <Card className="flex-1 gap-2 border-dashed p-5">
          <span className="text-xs font-medium tracking-wider text-muted-foreground">
            PROJEÇÃO DO PERÍODO
          </span>
          <span className="font-mono text-[22px] font-semibold">
            {formatNumber(result.projectedEmissionKg)} kg CO₂
          </span>
          <span className="text-[11px] text-muted-foreground">
            realizado + dias restantes pela grade
          </span>
        </Card>
      </div>
      <div className="flex gap-4">
        <MetricCard
          label="CONSUMO REALIZADO"
          value={`${formatNumber(result.totalEnergyKwh)} kWh`}
          subtitle={until ? `energia elétrica até ${until}` : 'energia elétrica'}
        />
        <MetricCard
          label="FATOR DE EMISSÃO SIN"
          value={avgFactor != null ? avgFactor.toFixed(4).replace('.', ',') : '—'}
          subtitle="tCO₂/MWh · média dos meses do período"
        />
        <MetricCard
          label="CUSTO ESTIMADO"
          value={`R$ ${formatNumber(estimatedCost)}`}
          subtitle={`tarifa média R$ ${TARIFF.toFixed(2).replace('.', ',')}/kWh`}
        />
      </div>
    </div>
  )
}

function exceptionsLabel(lab: EmissionResult['byLaboratory'][number]): string | null {
  const parts = [
    lab.cancelledClasses > 0 ? `${lab.cancelledClasses} canc.` : null,
    lab.adjustedClasses > 0 ? `${lab.adjustedClasses} ajust.` : null,
    lab.extraClasses > 0 ? `${lab.extraClasses} extra` : null,
  ].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : null
}

function LabDetailTable({ result }: { result: EmissionResult }) {
  const labRows = result.byLaboratory.map((lab) => {
    const totalWatts = lab.configurations.reduce((s, c) => s + c.consumptionWatts * c.quantity, 0)
    const pct = result.totalEmissionKg > 0 ? (lab.emissionKg / result.totalEmissionKg) * 100 : 0
    return { lab, totalWatts, pct }
  })

  const totals = {
    stations: labRows.reduce((s, r) => s + r.lab.stationCount, 0),
    watts: labRows.reduce((s, r) => s + r.totalWatts, 0),
    stationHours: labRows.reduce((s, r) => s + r.lab.stationHours, 0),
    exceptions: labRows.reduce(
      (s, r) => s + r.lab.cancelledClasses + r.lab.adjustedClasses + r.lab.extraClasses,
      0,
    ),
  }
  const until = result.realizedUntil ? formatDate(result.realizedUntil) : null
  const head = 'text-xs tracking-wider text-muted-foreground'

  return (
    <Card className="overflow-hidden">
      <div className="flex flex-col gap-1 px-6 py-5 border-b border-border">
        <h2 className="text-base font-semibold">Detalhamento por Laboratório</h2>
        <p className="text-[13px] text-muted-foreground">
          Decomposição do realizado{until ? ` até ${until}` : ''} por laboratório, com as exceções
          aplicadas
        </p>
      </div>
      <Table>
        <TableHeader>
          <TableRow className="bg-muted hover:bg-muted">
            <TableHead className={`px-6 ${head}`}>LABORATÓRIO</TableHead>
            <TableHead className={head}>ESTAÇÕES</TableHead>
            <TableHead className={head}>POTÊNCIA</TableHead>
            <TableHead className={head}>ESTAÇÕES-HORA</TableHead>
            <TableHead className={head}>USO MÉDIO</TableHead>
            <TableHead className={head}>EXCEÇÕES</TableHead>
            <TableHead className={head}>CONSUMO (KWH)</TableHead>
            <TableHead className={head}>EMISSÃO (KG CO₂)</TableHead>
            <TableHead className={`${head} text-right pr-6`}>% DO TOTAL</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {labRows.map((row, i) => {
            const exceptions = exceptionsLabel(row.lab)
            return (
              <TableRow key={row.lab.laboratoryId} className={i % 2 === 1 ? 'bg-[#fbfcf9]' : ''}>
                <TableCell className="px-6 text-[13px] font-semibold">
                  {row.lab.laboratoryName}
                </TableCell>
                <TableCell className="font-mono text-[13px]">{row.lab.stationCount}</TableCell>
                <TableCell className="font-mono text-xs text-muted-foreground">
                  {formatNumber(row.totalWatts)} W
                </TableCell>
                <TableCell className="font-mono text-[13px]">
                  {formatNumber(row.lab.stationHours)} h
                </TableCell>
                <TableCell className="font-mono text-[13px]">
                  {row.lab.averageUsagePct.toFixed(0)}%
                </TableCell>
                <TableCell>
                  {exceptions ? (
                    <Badge variant="outline">{exceptions}</Badge>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )}
                </TableCell>
                <TableCell className="font-mono text-[13px]">
                  {formatNumber(row.lab.energyKwh)}
                </TableCell>
                <TableCell className="font-mono text-[13px] font-semibold">
                  {formatNumber(row.lab.emissionKg)}
                </TableCell>
                <TableCell className="text-[13px] font-semibold text-primary text-right pr-6">
                  {row.pct.toFixed(1).replace('.', ',')}%
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
        <TableFooter className="bg-muted">
          <TableRow className="hover:bg-muted">
            <TableCell className="px-6 text-[13px] font-bold">Total</TableCell>
            <TableCell className="font-mono text-[13px] font-semibold">{totals.stations}</TableCell>
            <TableCell className="font-mono text-[13px] font-semibold">
              {formatNumber(totals.watts)} W
            </TableCell>
            <TableCell className="font-mono text-[13px] font-semibold">
              {formatNumber(totals.stationHours)} h
            </TableCell>
            <TableCell className="text-[13px] text-muted-foreground">—</TableCell>
            <TableCell className="text-[13px]">
              {totals.exceptions > 0 ? `${totals.exceptions} ajustes` : '—'}
            </TableCell>
            <TableCell className="font-mono text-[13px] font-semibold">
              {formatNumber(result.totalEnergyKwh)}
            </TableCell>
            <TableCell className="font-mono text-[13px] font-bold">
              {formatNumber(result.totalEmissionKg)}
            </TableCell>
            <TableCell className="text-[13px] font-bold text-primary text-right pr-6">
              100%
            </TableCell>
          </TableRow>
        </TableFooter>
      </Table>
    </Card>
  )
}

function formatNumber(n: number): string {
  return Math.round(n).toLocaleString('pt-BR')
}

export function EmissionsDashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const periodIdParam = searchParams.get('periodId')

  const { data: periodsPage } = useQuery({
    queryKey: ['academic-periods', 'all-for-emissions'],
    queryFn: () => listAcademicPeriods(0, 100),
  })

  const periods = periodsPage?.content ?? []

  const resolvedPeriodId = useMemo(() => {
    if (periodIdParam && periods.some((p) => p.id === periodIdParam)) return periodIdParam
    return periods[0]?.id ?? null
  }, [periods, periodIdParam])

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

  function handlePeriodChange(id: string) {
    setSearchParams({ periodId: id })
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
            <Select value={resolvedPeriodId ?? undefined} onValueChange={handlePeriodChange}>
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

              <ResultMetrics result={result} />

              <LabDetailTable result={result} />

              <ConsumptionSources sources={result.consumptionSources} />

              <EquivalenceCards
                carKm={result.equivalentCarKm}
                treesNeeded={result.equivalentTreesNeeded}
                totalEnergyKwh={result.totalEnergyKwh}
                basis={
                  result.realizedUntil
                    ? `com base no realizado até ${formatDate(result.realizedUntil)}`
                    : undefined
                }
              />
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}
