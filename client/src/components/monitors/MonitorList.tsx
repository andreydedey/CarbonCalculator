import { useInfiniteQuery } from '@tanstack/react-query'
import { Monitor as MonitorIcon, Search } from 'lucide-react'
import type React from 'react'
import { useSearchParams } from 'react-router-dom'
import { useDebounce } from 'use-debounce'
import { DeleteMonitorDialog } from '@/components/monitors/DeleteMonitorDialog'
import { MonitorCard } from '@/components/monitors/MonitorCard'
import { CardListSkeleton } from '@/components/ui/skeletons'
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
import { useDialog, useFormDialog } from '@/hooks/use-dialog'
import { listMonitors, type Monitor } from '@/lib/api/monitors'

interface MonitorListProps {
  formOpen?: boolean
  onFormOpenChange?: (open: boolean) => void
}

export const MonitorList: React.FC<MonitorListProps> = ({ formOpen, onFormOpenChange }) => {
  const [searchParams, setSearchParams] = useSearchParams()
  const formDialog = useFormDialog<Monitor>({
    createOpen: formOpen,
    onCreateOpenChange: onFormOpenChange,
  })
  const deleteDialog = useDialog<Monitor>()

  const search = searchParams.get('q') ?? ''
  const [debouncedSearch] = useDebounce(search, 400)

  function setSearch(value: string) {
    setSearchParams(
      (prev) => {
        if (value) prev.set('q', value)
        else prev.delete('q')
        return prev
      },
      { replace: true },
    )
  }

  const {
    data: monitorsData,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: ['monitors', debouncedSearch],
    queryFn: ({ pageParam }) => listMonitors({ name: debouncedSearch, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  })

  const monitors = monitorsData?.pages.flatMap((p) => p.content) ?? []

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
              <EmptyDescription>Comece cadastrando o primeiro monitor.</EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
              <Button onClick={() => onFormOpenChange?.(true)}>Cadastrar Monitor</Button>
            </EmptyContent>
          </Empty>
        )
      ) : (
        <div className="flex flex-col gap-4">
          {monitors.map((monitor) => (
            <MonitorCard
              key={monitor.id}
              monitor={monitor}
              onEdit={(m) => formDialog.openEdit(m)}
              onDelete={(m) => deleteDialog.openDialog(m)}
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
