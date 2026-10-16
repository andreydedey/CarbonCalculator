import { Monitor as MonitorIcon, Search } from 'lucide-react'
import type React from 'react'
import { DeleteMonitorDialog } from '@/components/monitors/DeleteMonitorDialog'
import { MonitorCard } from '@/components/monitors/MonitorCard'
import { MonitorForm } from '@/components/monitors/MonitorForm'
import { Button } from '@/components/ui/button'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import { Input } from '@/components/ui/input'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { CardListSkeleton } from '@/components/ui/skeletons'
import { useDialog, useFormDialog } from '@/hooks/use-dialog'
import { usePagedQuery } from '@/hooks/use-paged-query'
import { useSearchParam } from '@/hooks/use-search-param'
import { useCanManage } from '@/hooks/useCanManage'
import { listMonitors, type Monitor } from '@/lib/api/monitors'

interface MonitorListProps {
  formOpen?: boolean
  onFormOpenChange?: (open: boolean) => void
}

export const MonitorList: React.FC<MonitorListProps> = ({ formOpen, onFormOpenChange }) => {
  const canManage = useCanManage()
  const formDialog = useFormDialog<Monitor>({
    createOpen: formOpen,
    onCreateOpenChange: onFormOpenChange,
  })
  const deleteDialog = useDialog<Monitor>()

  const [search, debouncedSearch, setSearch] = useSearchParam()

  const {
    items: monitors,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = usePagedQuery({
    queryKey: ['monitors', debouncedSearch],
    queryFn: (page) => listMonitors({ name: debouncedSearch, page }),
  })

  function handleSaved() {
    formDialog.close()
    refetch()
  }

  function handleDeleted() {
    deleteDialog.closeDialog()
    refetch()
  }

  return (
    <div className="flex flex-col gap-6">
      <MonitorForm
        monitor={formDialog.editing ?? undefined}
        open={formDialog.open}
        onOpenChange={formDialog.onOpenChange}
        onSaved={handleSaved}
      />

      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar monitor..."
            className="h-9 pl-8"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {isLoading ? (
        <CardListSkeleton count={3} />
      ) : monitors.length === 0 ? (
        search.length > 0 ? (
          <Empty>
            <EmptyHeader>
              <EmptyMedia>
                <Search className="size-6 text-muted-foreground" />
              </EmptyMedia>
              <EmptyTitle>Nenhum resultado encontrado</EmptyTitle>
              <EmptyDescription>Tente ajustar os filtros ou o termo de busca.</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <Empty>
            <EmptyHeader>
              <EmptyMedia>
                <MonitorIcon className="size-6 text-muted-foreground" />
              </EmptyMedia>
              <EmptyTitle>Nenhum monitor cadastrado</EmptyTitle>
              <EmptyDescription>
                {canManage
                  ? 'Comece cadastrando o primeiro monitor.'
                  : 'Os registros aparecerão aqui quando um gestor cadastrá-los.'}
              </EmptyDescription>
            </EmptyHeader>
            {canManage && (
              <EmptyContent>
                <Button onClick={() => onFormOpenChange?.(true)}>Cadastrar Monitor</Button>
              </EmptyContent>
            )}
          </Empty>
        )
      ) : (
        <div className="flex flex-col gap-4">
          {monitors.map((monitor) => (
            <MonitorCard
              key={monitor.id}
              monitor={monitor}
              {...(canManage && {
                onEdit: (m: Monitor) => formDialog.openEdit(m),
                onDelete: (m: Monitor) => deleteDialog.openDialog(m),
              })}
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
        <DeleteMonitorDialog
          monitor={deleteDialog.data}
          open={deleteDialog.open}
          onOpenChange={deleteDialog.onOpenChange}
          onDeleted={handleDeleted}
        />
      )}
    </div>
  )
}
