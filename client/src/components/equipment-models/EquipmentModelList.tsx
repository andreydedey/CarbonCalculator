import { useInfiniteQuery } from '@tanstack/react-query'
import { Cpu, Plus, Search } from 'lucide-react'
import type React from 'react'
import { useSearchParams } from 'react-router-dom'
import { useDebounce } from 'use-debounce'
import { DeleteEquipmentModelDialog } from '@/components/equipment-models/DeleteEquipmentModelDialog'
import { EquipmentModelCard } from '@/components/equipment-models/EquipmentModelCard'
import { EquipmentModelForm } from '@/components/equipment-models/EquipmentModelForm'
import { SummaryCard } from '@/components/laboratories/LabCard'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import { useDialog } from '@/hooks/use-dialog'
import { type EquipmentModel, listEquipmentModels } from '@/lib/api/equipment-models'

export const EquipmentModelList: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams()
  const form = useDialog<EquipmentModel>()
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
  const totalModels = modelsData?.pages[0]?.totalElements ?? 0

  function handleSaved() {
    form.closeDialog()
    refetch()
  }

  function handleDeleted() {
    deleteDialog.closeDialog()
    refetch()
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-normal text-muted-foreground">
            Cadastro &rsaquo; Modelos de Equipamento
          </p>
          <h1 className="font-heading text-2xl font-bold">Modelos de Equipamento</h1>
        </div>
        <Button onClick={() => form.openDialog()}>
          <Plus className="size-4" />
          Novo Modelo
        </Button>
      </div>

      <EquipmentModelForm
        model={form.data ?? undefined}
        open={form.open}
        onOpenChange={(open) => !open && form.closeDialog()}
        onSaved={handleSaved}
      />

      <div className="flex gap-4">
        <SummaryCard
          value={String(totalModels)}
          label="Total de Modelos"
          icon={<Cpu className="size-[18px] text-primary-foreground" />}
          variant="accent"
        />
      </div>

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
        <p className="text-sm text-muted-foreground">Nenhum modelo encontrado.</p>
      ) : (
        <div className="flex flex-col gap-4">
          {models.map((model) => (
            <EquipmentModelCard
              key={model.id}
              model={model}
              onEdit={(m) => form.openDialog(m)}
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
          onOpenChange={(open) => !open && deleteDialog.closeDialog()}
          onDeleted={handleDeleted}
        />
      )}
    </div>
  )
}
