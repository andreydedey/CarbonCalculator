import { useQuery } from '@tanstack/react-query'
import { Leaf, Zap } from 'lucide-react'
import { useMemo, useState } from 'react'
import { BreakdownCards } from '@/components/emissions/BreakdownCards'
import { EmissionsByLab } from '@/components/emissions/EmissionsByLab'
import { EmissionsByMonth } from '@/components/emissions/EmissionsByMonth'
import { EquivalenceCards } from '@/components/emissions/EquivalenceCards'
import { ExportButton } from '@/components/emissions/ExportButton'
import { ReadinessCheck } from '@/components/emissions/ReadinessCheck'
import { TransparencyPanel } from '@/components/emissions/TransparencyPanel'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { listAcademicPeriods } from '@/lib/api/academic-periods'
import { getEmissions, getReadiness } from '@/lib/api/emissions'

export function EmissionsDashboardPage() {
  const [selectedPeriodId, setSelectedPeriodId] = useState<string | null>(null)

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

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-normal text-muted-foreground">Análise &rsaquo; Emissões</p>
          <h1 className="font-heading text-2xl font-bold">Emissões de CO₂</h1>
        </div>
        <div className="flex items-center gap-3">
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
              {/* Summary cards */}
              <div className="grid grid-cols-2 gap-4">
                <Card size="sm">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-sm">
                      <Leaf className="size-4 text-muted-foreground" />
                      Emissão total
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-3xl font-bold">{result.totalEmissionKg.toFixed(2)}</p>
                    <p className="text-xs text-muted-foreground">kgCO₂</p>
                  </CardContent>
                </Card>
                <Card size="sm">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-sm">
                      <Zap className="size-4 text-muted-foreground" />
                      Energia total
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-3xl font-bold">{result.totalEnergyKwh.toFixed(1)}</p>
                    <p className="text-xs text-muted-foreground">kWh</p>
                  </CardContent>
                </Card>
              </div>

              <EquivalenceCards
                carKm={result.equivalences.carKm}
                treesNeeded={result.equivalences.treesNeeded}
              />

              <EmissionsByMonth data={result.byMonth} />

              <EmissionsByLab data={result.byLaboratory} totalEmission={result.totalEmissionKg} />

              <BreakdownCards
                byShift={result.byShift}
                byDayOfWeek={result.byDayOfWeek}
                byEquipmentModel={result.byEquipmentModel}
                byMonitorModel={result.byMonitorModel}
                byOperatingSystem={result.byOperatingSystem}
              />

              <TransparencyPanel inputs={result.inputs} />
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}
