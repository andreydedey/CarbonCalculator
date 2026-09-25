import { useInfiniteQuery, useMutation } from '@tanstack/react-query'
import { Calendar, Plus } from 'lucide-react'
import type React from 'react'
import { toast } from 'sonner'
import { AcademicPeriodCard } from '@/components/academic-periods/AcademicPeriodCard'
import { AcademicPeriodForm } from '@/components/academic-periods/AcademicPeriodForm'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { useDialog } from '@/hooks/use-dialog'
import {
  type AcademicPeriod,
  deleteAcademicPeriod,
  listAcademicPeriods,
} from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'

export const AcademicPeriodsPage: React.FC = () => {
  const form = useDialog<AcademicPeriod>()
  const deleteDialog = useDialog<AcademicPeriod>()

  const {
    data: periodsData,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: ['academic-periods'],
    queryFn: ({ pageParam }) => listAcademicPeriods(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  })

  const periods = periodsData?.pages.flatMap((p) => p.content) ?? []

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAcademicPeriod(id),
    onSuccess: () => {
      deleteDialog.closeDialog()
      refetch()
      toast.success('Período excluído.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível excluir o período.')
    },
  })

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-normal text-muted-foreground">
            Cadastro &rsaquo; Calendário Letivo
          </p>
          <h1 className="font-heading text-2xl font-bold">Calendário Letivo</h1>
        </div>
        <Button onClick={() => form.openDialog()}>
          <Plus className="size-4" />
          Novo Período
        </Button>
      </div>

      <AcademicPeriodForm
        period={form.data ?? undefined}
        open={form.open}
        onOpenChange={(open) => !open && form.closeDialog()}
        onSaved={() => {
          form.closeDialog()
          refetch()
        }}
      />

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : periods.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-3 py-12">
          <Calendar className="size-12 text-muted-foreground/40" />
          <p className="text-sm text-muted-foreground">Nenhum período letivo cadastrado.</p>
          <Button variant="outline" size="sm" onClick={() => form.openDialog()}>
            <Plus className="size-4" />
            Criar primeiro período
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {periods.map((period) => (
            <AcademicPeriodCard
              key={period.id}
              period={period}
              onEdit={(p) => form.openDialog(p)}
              onDelete={(p) => deleteDialog.openDialog(p)}
            />
          ))}
        </div>
      )}

      <LoadMoreButton
        fetchNextPage={fetchNextPage}
        hasNextPage={hasNextPage}
        isFetchingNextPage={isFetchingNextPage}
      />

      {deleteDialog.data && (
        <Dialog
          open={deleteDialog.open}
          onOpenChange={(open) => !open && deleteDialog.closeDialog()}
        >
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>Excluir Período</DialogTitle>
              <DialogDescription>
                Tem certeza que deseja excluir o período <strong>{deleteDialog.data.name}</strong>?
                Feriados e grades de ocupação associados também serão removidos.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => deleteDialog.closeDialog()}>
                Cancelar
              </Button>
              <Button
                variant="destructive"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(deleteDialog.data!.id)}
              >
                Excluir
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  )
}
