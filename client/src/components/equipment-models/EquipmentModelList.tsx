import { useInfiniteQuery } from '@tanstack/react-query'
import { Cpu, Search } from 'lucide-react'
import type React from 'react'
import { useSearchParams } from 'react-router-dom'
import { useDebounce } from 'use-debounce'
import { DeleteEquipmentModelDialog } from '@/components/equipment-models/DeleteEquipmentModelDialog'
import { EquipmentModelCard } from '@/components/equipment-models/EquipmentModelCard'
import { EquipmentModelForm } from '@/components/equipment-models/EquipmentModelForm'
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
import { type EquipmentModel, listEquipmentModels } from '@/lib/api/equipment-models'

interface EquipmentModelListProps {
  formOpen?: boolean
  onFormOpenChange?: (open: boolean) => void
}

export const EquipmentModelList: React.FC<EquipmentModelListProps> = ({
  formOpen,
  onFormOpenChange,
}) => {
  const [searchParams, setSearchParams] = useSearchParams()
  const formDialog = useFormDialog<EquipmentModel>({
    createOpen: formOpen,
    onCreateOpenChange: onFormOpenChange,
  })
  const deleteDialog = useDialog<EquipmentModel>()

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
    data: modelsData,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: ['equipment-models', debouncedSearch],
    queryFn: ({ pageParam }) => listEquipmentModels({ name: debouncedSearch, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  })

  const models = modelsData?.pages.flatMap((p) => p.content) ?? []

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
      <EquipmentModelForm
        model={formDialog.editing ?? undefined}
        open={formDialog.open}
        onOpenChange={formDialog.onOpenChange}
        onSaved={handleSaved}
      />

      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar modelo..."
            className="h-9 pl-8"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : models.length === 0 ? (
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
                <Cpu className="size-6 text-muted-foreground" />
              </EmptyMedia>
              <EmptyTitle>Nenhum modelo cadastrado</EmptyTitle>
              <EmptyDescription>
                Comece cadastrando o primeiro modelo de equipamento.
              </EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
              <Button onClick={() => onFormOpenChange?.(true)}>Cadastrar Modelo</Button>
            </EmptyContent>
          </Empty>
        )
      ) : (
        <div className="flex flex-col gap-4">
          {models.map((model) => (
            <EquipmentModelCard
              key={model.id}
              model={model}
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
        <DeleteEquipmentModelDialog
          model={deleteDialog.data}
          open={deleteDialog.open}
          onOpenChange={deleteDialog.onOpenChange}
          onDeleted={handleDeleted}
        />
      )}
    </div>
  )
}
