import { useQuery } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import type React from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { HolidayEditor } from '@/components/academic-periods/HolidayEditor'
import { PeriodSummary } from '@/components/academic-periods/PeriodSummary'
import { ScheduleGrid } from '@/components/academic-periods/ScheduleGrid'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { getAcademicPeriod } from '@/lib/api/academic-periods'
import { listLaboratories } from '@/lib/api/laboratories'

function formatDate(dateStr: string): string {
  const [year, month, day] = dateStr.split('-')
  return `${day}/${month}/${year}`
}

export const AcademicPeriodDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: period, isLoading: periodLoading } = useQuery({
    queryKey: ['academic-period', id],
    queryFn: () => getAcademicPeriod(id!),
    enabled: !!id,
  })

  const { data: laboratoriesData } = useQuery({
    queryKey: ['laboratories-all'],
    queryFn: () => listLaboratories({ size: 100 }),
  })

  const laboratories = laboratoriesData?.content ?? []

  if (periodLoading) {
    return <p className="text-sm text-muted-foreground">Carregando...</p>
  }

  if (!period) {
    return <p className="text-sm text-muted-foreground">Período não encontrado.</p>
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/academic-periods')}>
          <ArrowLeft className="size-4" />
        </Button>
        <div className="flex flex-col gap-0.5">
          <p className="text-xs font-normal text-muted-foreground">
            Cadastro &rsaquo; Calendário Letivo &rsaquo; {period.name}
          </p>
          <h1 className="font-heading text-2xl font-bold">{period.name}</h1>
          <p className="text-sm text-muted-foreground">
            {formatDate(period.startDate)} — {formatDate(period.endDate)}
          </p>
        </div>
      </div>

      <Tabs defaultValue="summary">
        <TabsList variant="line">
          <TabsTrigger value="summary">Resumo</TabsTrigger>
          <TabsTrigger value="holidays">Feriados</TabsTrigger>
          <TabsTrigger value="schedules">Grades de Ocupação</TabsTrigger>
        </TabsList>

        <TabsContent value="summary" className="mt-4">
          <PeriodSummary periodId={period.id} />
        </TabsContent>

        <TabsContent value="holidays" className="mt-4">
          <HolidayEditor period={period} />
        </TabsContent>

        <TabsContent value="schedules" className="mt-4">
          {laboratories.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Nenhum laboratório cadastrado na instituição.
            </p>
          ) : (
            <ScheduleGrid
              periodId={period.id}
              periodName={period.name}
              laboratories={laboratories.map((l) => ({ id: l.id, name: l.name }))}
            />
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
