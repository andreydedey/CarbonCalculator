import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Factory, FlaskConical, Leaf, Zap } from 'lucide-react'
import type React from 'react'
import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { TodayClassesCard } from '@/components/dashboard/TodayClassesCard'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { useCanManage } from '@/hooks/useCanManage'
import { type AcademicPeriod, listAcademicPeriods } from '@/lib/api/academic-periods'
import { getEmissions } from '@/lib/api/emissions'
import { listLaboratories } from '@/lib/api/laboratories'
import { formatDate, formatDayMonth, toIsoDate } from '@/lib/utils/occupation'

function formatNumber(n: number): string {
  return Math.round(n).toLocaleString('pt-BR')
}

function Metric({
  label,
  value,
  subtitle,
  icon: Icon,
  highlighted,
}: {
  label: string
  value: string
  subtitle: string
  icon: React.ComponentType<{ className?: string }>
  highlighted?: boolean
}) {
  return (
    <Card className={`flex-1 gap-3 p-5 ${highlighted ? 'bg-accent' : ''}`}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium tracking-wider text-muted-foreground">{label}</span>
        <Icon className="size-4 text-primary" />
      </div>
      <span className="font-mono text-[22px] font-semibold">{value}</span>
      <span className="text-[11px] text-muted-foreground">{subtitle}</span>
    </Card>
  )
}

/** The period that contains today, otherwise the most recent one. */
function currentPeriod(periods: AcademicPeriod[], today: string): AcademicPeriod | null {
  return (
    periods.find((p) => p.startDate <= today && today <= p.endDate) ??
    [...periods].sort((a, b) => b.startDate.localeCompare(a.startDate))[0] ??
    null
  )
}

export function DashboardPage() {
  const canManage = useCanManage()
  const today = toIsoDate(new Date())

  const { data: periodsPage } = useQuery({
    queryKey: ['academic-periods', 'all-for-dashboard'],
    queryFn: () => listAcademicPeriods(0, 100),
  })
  const period = useMemo(
    () => currentPeriod(periodsPage?.content ?? [], today),
    [periodsPage, today],
  )

  const { data: labsPage } = useQuery({
    queryKey: ['laboratories', 'all'],
    queryFn: () => listLaboratories({ status: 'active', size: 100 }),
  })
  const laboratories = labsPage?.content ?? []

  const { data: result, refetch: refetchEmissions } = useQuery({
    queryKey: ['emissions', period?.id],
    queryFn: () => getEmissions((period as AcademicPeriod).id),
    enabled: !!period,
  })

  const topEmitter = result?.byLaboratory.reduce<(typeof result.byLaboratory)[number] | null>(
    (top, lab) => (top == null || lab.emissionKg > top.emissionKg ? lab : top),
    null,
  )
  const currentFactor = result?.emissionFactors.find((f) => f.month === today.substring(0, 7))
  const labsWithClasses = result?.byLaboratory.filter((l) => l.stationHours > 0).length ?? 0

  return (
    <div className="flex flex-col gap-7">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs text-muted-foreground">Dashboard</p>
          <h1 className="font-heading text-2xl font-bold">Visão Geral de Emissões</h1>
        </div>
        {period && (
          <div className="flex items-center gap-1.5 rounded-md bg-accent px-3.5 py-1.5 text-xs font-medium text-primary">
            <CalendarDays className="size-3.5" />
            {period.name} · hoje, {formatDayMonth(today)}
          </div>
        )}
      </div>

      {!period ? (
        <p className="text-sm text-muted-foreground italic">
          Nenhum período letivo cadastrado. Cadastre um período no Calendário para acompanhar as
          emissões.
        </p>
      ) : (
        <>
          <div className="flex gap-4">
            <Metric
              highlighted
              icon={Leaf}
              label="EMISSÃO REALIZADA"
              value={result ? `${formatNumber(result.totalEmissionKg)} kg CO₂` : '—'}
              subtitle={
                result?.realizedUntil
                  ? `${period.name} · até ${formatDate(result.realizedUntil)} · ${result.schoolDaysElapsed} de ${result.schoolDaysTotal} dias letivos`
                  : `${period.name} · período ainda não começou`
              }
            />
            <Metric
              icon={FlaskConical}
              label="LABORATÓRIOS ATIVOS"
              value={`${labsWithClasses} / ${laboratories.length}`}
              subtitle="com aulas registradas no período"
            />
            <Metric
              icon={Factory}
              label="MAIOR EMISSOR"
              value={topEmitter && topEmitter.emissionKg > 0 ? topEmitter.laboratoryName : '—'}
              subtitle={
                topEmitter && topEmitter.emissionKg > 0
                  ? `${formatNumber(topEmitter.emissionKg)} kg CO₂ no período`
                  : 'sem emissões realizadas ainda'
              }
            />
            <Metric
              icon={Zap}
              label="FATOR DE EMISSÃO"
              value={currentFactor ? `${currentFactor.value.toFixed(4).replace('.', ',')}` : '—'}
              subtitle={
                currentFactor ? 'tCO₂/MWh · mês atual (SIN)' : 'fator do mês não cadastrado'
              }
            />
          </div>

          <TodayClassesCard
            period={period}
            laboratories={laboratories}
            canEdit={canManage}
            onChanged={() => refetchEmissions()}
          />

          {result && result.byLaboratory.length > 0 && (
            <div className="flex flex-col gap-4">
              <h2 className="text-[15px] font-semibold">Laboratórios</h2>
              <div className="grid grid-cols-3 gap-4">
                {result.byLaboratory.map((lab) => (
                  <Card key={lab.laboratoryId} className="gap-3 p-5">
                    <span className="text-[15px] font-semibold">{lab.laboratoryName}</span>
                    <span className="font-mono text-lg font-semibold">
                      {formatNumber(lab.emissionKg)} kg CO₂ no período
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {lab.stationCount} estações · {lab.averageUsagePct.toFixed(0)}% de uso médio
                    </span>
                    <Progress
                      value={
                        result.totalEmissionKg > 0
                          ? (lab.emissionKg / result.totalEmissionKg) * 100
                          : 0
                      }
                      className="h-1.5"
                    />
                    <Button asChild size="sm" className="w-fit">
                      <Link to={`/emissions?periodId=${period.id}`}>Ver detalhes</Link>
                    </Button>
                  </Card>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
