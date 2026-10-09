import { useInfiniteQuery, useMutation } from '@tanstack/react-query'
import { Pencil, Plug, Trash2 } from 'lucide-react'
import type React from 'react'
import { toast } from 'sonner'
import { ConsumptionMeasurementForm } from '@/components/consumption-measurements/ConsumptionMeasurementForm'
import { Badge } from '@/components/ui/badge'
import { TableSkeleton } from '@/components/ui/skeletons'
import { Button } from '@/components/ui/button'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { useFormDialog } from '@/hooks/use-dialog'
import { isApiError } from '@/lib/api/client'
import {
  type ConsumptionMeasurement,
  deleteConsumptionMeasurement,
  listConsumptionMeasurements,
  type TargetType,
} from '@/lib/api/consumption-measurements'
import { formatDate } from '@/lib/utils/occupation'

interface ConsumptionMeasurementListProps {
  formOpen?: boolean
  onFormOpenChange?: (open: boolean) => void
}

const TARGET_LABELS: Record<TargetType, string> = {
  COMPUTER: 'Computador',
  MONITOR: 'Monitor',
  COMBINED: 'Conjunto',
}

const TARGET_VARIANTS: Record<TargetType, 'default' | 'secondary' | 'outline'> = {
  COMPUTER: 'default',
  MONITOR: 'secondary',
  COMBINED: 'outline',
}

function targetLabel(m: ConsumptionMeasurement): string {
  const parts: string[] = []
  if (m.equipmentModel) parts.push(m.equipmentModel.name)
  if (m.operatingSystem) parts.push(m.operatingSystem.name)
  if (m.monitor) parts.push(m.monitor.name)
  return parts.join(' + ')
}

export const ConsumptionMeasurementList: React.FC<ConsumptionMeasurementListProps> = ({
  formOpen,
  onFormOpenChange,
}) => {
  const formDialog = useFormDialog<ConsumptionMeasurement>({
    createOpen: formOpen,
    onCreateOpenChange: onFormOpenChange,
  })

  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage, refetch } =
    useInfiniteQuery({
      queryKey: ['consumption-measurements'],
      queryFn: ({ pageParam }) => listConsumptionMeasurements({ page: pageParam }),
      initialPageParam: 0,
      getNextPageParam: (lastPage) =>
        lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    })

  const measurements = data?.pages.flatMap((p) => p.content) ?? []

  const deleteMutation = useMutation({
    mutationFn: deleteConsumptionMeasurement,
    onSuccess: () => {
      refetch()
      toast.success('Medição excluída.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível excluir a medição.')
    },
  })

  function handleSaved() {
    formDialog.close()
    refetch()
  }

  const total = data?.pages[0]?.totalElements ?? 0

  return (
    <div className="flex flex-col gap-6">
      <ConsumptionMeasurementForm
        measurement={formDialog.editing ?? undefined}
        open={formDialog.open}
        onOpenChange={formDialog.onOpenChange}
        onSaved={handleSaved}
      />

      {isLoading ? (
        <TableSkeleton rows={4} columns={6} />
      ) : measurements.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyMedia>
              <Plug className="size-6 text-muted-foreground" />
            </EmptyMedia>
            <EmptyTitle>Nenhuma medição registrada</EmptyTitle>
            <EmptyDescription>Comece registrando a primeira medição de consumo.</EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button onClick={() => onFormOpenChange?.(true)}>Registrar Medição</Button>
          </EmptyContent>
        </Empty>
      ) : (
        <>
          <div className="rounded-lg border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-2 text-left font-medium">TIPO</th>
                  <th className="px-4 py-2 text-left font-medium">ALVO</th>
                  <th className="px-4 py-2 text-right font-medium">CONSUMO (W)</th>
                  <th className="px-4 py-2 text-right font-medium">DURAÇÃO</th>
                  <th className="px-4 py-2 text-left font-medium">DATA</th>
                  <th className="px-4 py-2 text-right font-medium">AÇÕES</th>
                </tr>
              </thead>
              <tbody>
                {measurements.map((m) => (
                  <tr key={m.id} className="border-b last:border-b-0">
                    <td className="px-4 py-2">
                      <Badge variant={TARGET_VARIANTS[m.targetType]}>
                        {TARGET_LABELS[m.targetType]}
                      </Badge>
                    </td>
                    <td className="px-4 py-2 font-medium">{targetLabel(m)}</td>
                    <td className="px-4 py-2 text-right font-mono text-sm font-semibold text-primary">
                      {m.averageWatts.toFixed(1)} W
                    </td>
                    <td className="px-4 py-2 text-right text-sm text-muted-foreground">
                      {m.durationMinutes} min
                    </td>
                    <td className="px-4 py-2 text-sm text-muted-foreground">
                      {formatDate(m.measurementDate)}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-7"
                          onClick={() => formDialog.openEdit(m)}
                          title="Editar medição"
                        >
                          <Pencil className="size-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-7 text-destructive"
                          onClick={() => deleteMutation.mutate(m.id)}
                          title="Excluir medição"
                        >
                          <Trash2 className="size-3.5" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="text-xs text-muted-foreground">
            Exibindo {measurements.length} de {total} medições
          </div>
        </>
      )}

      <LoadMoreButton
        fetchNextPage={fetchNextPage}
        hasNextPage={hasNextPage}
        isFetchingNextPage={isFetchingNextPage}
      />
    </div>
  )
}
