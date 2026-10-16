import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Calendar, Plus } from 'lucide-react'
import type React from 'react'
import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { AcademicPeriodCard } from '@/components/academic-periods/AcademicPeriodCard'
import { AcademicPeriodForm } from '@/components/academic-periods/AcademicPeriodForm'
import { CopyPeriodDialog } from '@/components/academic-periods/CopyPeriodDialog'
import { DeletePeriodDialog } from '@/components/academic-periods/DeletePeriodDialog'
import { HolidayEditor } from '@/components/academic-periods/HolidayEditor'
import { OccupationSummary } from '@/components/academic-periods/OccupationSummary'
import { ShiftConfigModal } from '@/components/academic-periods/ShiftConfigModal'
import { ShiftSummaryTable } from '@/components/academic-periods/ShiftSummaryTable'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { CardGridSkeleton } from '@/components/ui/skeletons'
import { useDialog } from '@/hooks/use-dialog'
import { usePagedQuery } from '@/hooks/use-paged-query'
import { useCanManage } from '@/hooks/useCanManage'
import {
  type AcademicPeriod,
  deleteAcademicPeriod,
  listAcademicPeriods,
} from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'

export const AcademicPeriodsPage: React.FC = () => {
  const navigate = useNavigate()
  const canManage = useCanManage()
  const queryClient = useQueryClient()
  const form = useDialog<AcademicPeriod>()
  const deleteDialog = useDialog<AcademicPeriod>()
  const copyDialog = useDialog<AcademicPeriod>()
  const [shiftModalPeriod, setShiftModalPeriod] = useState<AcademicPeriod | null>(null)
  const [selectedPeriodId, setSelectedPeriodId] = useState<string | null>(null)
  const [addHolidayOpen, setAddHolidayOpen] = useState(false)

  const {
    items: periods,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = usePagedQuery({
    queryKey: ['academic-periods'],
    queryFn: (page) => listAcademicPeriods(page),
  })

  const resolvedPeriodId = useMemo(() => {
    if (selectedPeriodId && periods.some((p) => p.id === selectedPeriodId)) {
      return selectedPeriodId
    }
    if (periods.length === 0) return null
    const today = new Date().toISOString().split('T')[0]
    const active = periods.find((p) => p.startDate <= today && p.endDate >= today)
    return active?.id ?? periods[0].id
  }, [periods, selectedPeriodId])

  const selectedPeriod = useMemo(
    () => periods.find((p) => p.id === resolvedPeriodId) ?? null,
    [periods, resolvedPeriodId],
  )

  const handleSelect = useCallback((period: AcademicPeriod) => {
    setSelectedPeriodId(period.id)
  }, [])

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAcademicPeriod(id),
    onSuccess: () => {
      deleteDialog.closeDialog()
      if (selectedPeriodId === deleteDialog.data?.id) {
        setSelectedPeriodId(null)
      }
      refetch()
      toast.success('Período excluído.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível excluir o período.')
    },
  })

  return (
    <div className="flex flex-col gap-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-0.5">
          <h1 className="font-heading text-2xl font-bold">Calendário Letivo</h1>
        </div>
        {canManage && (
          <Button onClick={() => form.openDialog()}>
            <Plus className="size-4" />
            Novo Período
          </Button>
        )}
      </div>

      {/* Period cards — 2 column grid */}
      {isLoading ? (
        <CardGridSkeleton count={4} columns={2} />
      ) : periods.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia>
              <Calendar className="size-6 text-muted-foreground" />
            </EmptyMedia>
            <EmptyTitle>Nenhum período letivo cadastrado</EmptyTitle>
            <EmptyDescription>
              {canManage
                ? 'Comece cadastrando o primeiro período letivo.'
                : 'Os períodos aparecerão aqui quando um gestor cadastrá-los.'}
            </EmptyDescription>
          </EmptyHeader>
          {canManage && (
            <EmptyContent>
              <Button onClick={() => form.openDialog()}>Criar Período</Button>
            </EmptyContent>
          )}
        </Empty>
      ) : (
        <>
          <div className="flex gap-4 overflow-x-auto px-1 py-2">
            {periods.map((period) => (
              <AcademicPeriodCard
                key={period.id}
                period={period}
                selected={period.id === resolvedPeriodId}
                onSelect={handleSelect}
                {...(canManage && {
                  onEdit: (p: AcademicPeriod) => form.openDialog(p),
                  onConfigureShifts: (p: AcademicPeriod) => setShiftModalPeriod(p),
                })}
              />
            ))}
          </div>

          <LoadMoreButton
            fetchNextPage={fetchNextPage}
            hasNextPage={hasNextPage}
            isFetchingNextPage={isFetchingNextPage}
          />

          {/* Selected period sections */}
          {selectedPeriod && (
            <>
              {/* Section: Turnos e Horários de Aula */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Turnos e Horários de Aula</CardTitle>
                  <CardDescription>
                    Configuração dos períodos de funcionamento dos laboratórios
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <ShiftSummaryTable shifts={selectedPeriod.shifts} />
                </CardContent>
              </Card>

              {/* Section: Ocupação dos Laboratórios */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Ocupação dos Laboratórios</CardTitle>
                  <CardDescription>
                    Aulas efetivamente ministradas em cada laboratório — base para as horas de uso
                    do cálculo
                  </CardDescription>
                  <CardAction className="self-center">
                    <Button
                      size="sm"
                      onClick={() => navigate(`/academic-periods/${selectedPeriod.id}/occupation`)}
                    >
                      {canManage ? 'Configurar Ocupação' : 'Ver Ocupação'}
                    </Button>
                  </CardAction>
                </CardHeader>
                <CardContent>
                  <OccupationSummary periodId={selectedPeriod.id} shifts={selectedPeriod.shifts} />
                </CardContent>
              </Card>

              {/* Section: Feriados e Recessos */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Feriados e Recessos</CardTitle>
                  <CardDescription>
                    Dias não letivos que serão descontados do cálculo de emissões
                  </CardDescription>
                  {canManage && (
                    <CardAction className="self-center">
                      <Button variant="outline" onClick={() => setAddHolidayOpen(true)}>
                        <Plus className="size-4" />
                        Adicionar
                      </Button>
                    </CardAction>
                  )}
                </CardHeader>
                <CardContent>
                  <HolidayEditor
                    period={selectedPeriod}
                    addDialogOpen={addHolidayOpen}
                    onAddDialogOpenChange={setAddHolidayOpen}
                    readOnly={!canManage}
                  />
                </CardContent>
              </Card>
            </>
          )}
        </>
      )}

      {/* Dialogs */}
      <AcademicPeriodForm
        period={form.data ?? undefined}
        open={form.open}
        onOpenChange={(open) => !open && form.closeDialog()}
        onSaved={() => {
          form.closeDialog()
          refetch()
        }}
      />

      {copyDialog.data && (
        <CopyPeriodDialog
          sourcePeriod={copyDialog.data}
          open={copyDialog.open}
          onOpenChange={(open) => !open && copyDialog.closeDialog()}
          onCopied={() => {
            copyDialog.closeDialog()
            refetch()
          }}
        />
      )}

      {shiftModalPeriod && (
        <ShiftConfigModal
          periodId={shiftModalPeriod.id}
          currentShifts={shiftModalPeriod.shifts}
          open={!!shiftModalPeriod}
          onOpenChange={(open) => !open && setShiftModalPeriod(null)}
          onSaved={() => {
            setShiftModalPeriod(null)
            queryClient.invalidateQueries({ queryKey: ['academic-periods'] })
          }}
        />
      )}

      {deleteDialog.data && (
        <DeletePeriodDialog
          periodName={deleteDialog.data.name}
          open={deleteDialog.open}
          onOpenChange={(open) => !open && deleteDialog.closeDialog()}
          onConfirm={() => deleteMutation.mutate(deleteDialog.data!.id)}
          isPending={deleteMutation.isPending}
        />
      )}
    </div>
  )
}

export default AcademicPeriodsPage
