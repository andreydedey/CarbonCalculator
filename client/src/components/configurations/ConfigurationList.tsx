import { useInfiniteQuery, useMutation } from '@tanstack/react-query'
import { Pencil, Trash2 } from 'lucide-react'
import type React from 'react'
import { toast } from 'sonner'
import { ConfigurationForm } from '@/components/configurations/ConfigurationForm'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { useDialog } from '@/hooks/use-dialog'
import { isApiError } from '@/lib/api/client'
import {
  type Configuration,
  deleteConfiguration,
  listConfigurations,
} from '@/lib/api/configurations'

interface ConfigurationListProps {
  formOpen?: boolean
  onFormOpenChange?: (open: boolean) => void
}

function usageLabel(config: Configuration): string {
  if (config.usageLabCount === 0) return 'Não utilizada'
  const labs =
    config.usageLabCount === 1
      ? '1 laboratório'
      : `${config.usageLabCount} laboratórios`
  const stations =
    config.usageStationCount === 1
      ? '1 estação'
      : `${config.usageStationCount} estações`
  return `${labs} · ${stations}`
}

export const ConfigurationList: React.FC<ConfigurationListProps> = ({
  formOpen,
  onFormOpenChange,
}) => {
  const editDialog = useDialog<Configuration>()

  const {
    data: configurationsData,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: ['configurations'],
    queryFn: ({ pageParam }) => listConfigurations({ page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  })

  const configurations = configurationsData?.pages.flatMap((p) => p.content) ?? []

  const deleteMutation = useMutation({
    mutationFn: deleteConfiguration,
    onSuccess: () => {
      refetch()
      toast.success('Configuração excluída.')
    },
    onError: (error) => {
      toast.error(
        isApiError(error) ? error.message : 'Não foi possível excluir a configuração.',
      )
    },
  })

  const isCreateFormOpen = formOpen ?? false
  const isFormOpen = isCreateFormOpen || editDialog.open

  function handleFormOpenChange(open: boolean) {
    if (!open) {
      onFormOpenChange?.(false)
      editDialog.closeDialog()
    }
  }

  function handleSaved() {
    onFormOpenChange?.(false)
    editDialog.closeDialog()
    refetch()
  }

  const totalConfigs = configurationsData?.pages[0]?.totalElements ?? 0
  const inUse = configurations.filter((c) => c.usageLabCount > 0).length
  const withoutMonitor = configurations.filter(
    (c) => c.monitor === null && !c.equipmentModel.hasIntegratedScreen,
  ).length

  return (
    <div className="flex flex-col gap-6">
      <ConfigurationForm
        configuration={editDialog.data ?? undefined}
        open={isFormOpen}
        onOpenChange={handleFormOpenChange}
        onSaved={handleSaved}
      />

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : configurations.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhuma configuração encontrada.</p>
      ) : (
        <>
          <div className="rounded-lg border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-2 text-left font-medium">COMPUTADOR</th>
                  <th className="px-4 py-2 text-left font-medium">SISTEMA OP.</th>
                  <th className="px-4 py-2 text-left font-medium">MONITOR</th>
                  <th className="px-4 py-2 text-left font-medium">EM USO EM</th>
                  <th className="px-4 py-2 text-right font-medium">AÇÕES</th>
                </tr>
              </thead>
              <tbody>
                {configurations.map((config) => (
                  <tr key={config.id} className="border-b last:border-b-0">
                    <td className="px-4 py-2 font-medium">{config.equipmentModel.name}</td>
                    <td className="px-4 py-2">
                      <Badge variant="secondary">{config.operatingSystem}</Badge>
                    </td>
                    <td className="px-4 py-2 text-muted-foreground">
                      {config.equipmentModel.hasIntegratedScreen
                        ? 'Tela integrada'
                        : config.monitor
                          ? config.monitor.name
                          : '—'}
                    </td>
                    <td className="px-4 py-2 text-xs text-muted-foreground">
                      {usageLabel(config)}
                    </td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-7"
                          onClick={() => editDialog.openDialog(config)}
                          title="Editar configuração"
                        >
                          <Pencil className="size-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-7 text-destructive"
                          onClick={() => deleteMutation.mutate(config.id)}
                          disabled={config.usageLabCount > 0}
                          title={
                            config.usageLabCount > 0
                              ? 'Remova de todos os laboratórios antes de excluir'
                              : 'Excluir configuração'
                          }
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
            Exibindo {configurations.length} de {totalConfigs} configurações · {inUse} em uso
            {withoutMonitor > 0 && ` · ${withoutMonitor} sem monitor`}
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
