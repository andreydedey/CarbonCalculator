import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query'
import { Cpu, Leaf, Monitor, Plus, Search } from 'lucide-react'
import type React from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useDebounce } from 'use-debounce'
import { DeactivateDialog } from '@/components/laboratories/DeactivateDialog'
import { DeleteDialog } from '@/components/laboratories/DeleteDialog'
import { LabCard, SummaryCard } from '@/components/laboratories/LabCard'
import { LaboratoryForm } from '@/components/laboratories/LaboratoryForm'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { LoadMoreButton } from '@/components/ui/load-more-button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useInstitution } from '@/context/InstitutionContext'
import { useDialog } from '@/hooks/use-dialog'
import { getInstitution } from '@/lib/api/institutions'
import {
  activateLaboratory,
  type Laboratory,
  type LabStatusFilter,
  listLaboratories,
} from '@/lib/api/laboratories'

const STATUS_OPTIONS: { value: LabStatusFilter; label: string }[] = [
  { value: 'all', label: 'Status: Todos' },
  { value: 'active', label: 'Apenas ativos' },
  { value: 'inactive', label: 'Apenas inativos' },
]

function statusLabel(lab: Laboratory): string {
  return lab.active ? 'Operando' : 'Inativo'
}

export const LaboratoryList: React.FC = () => {
  const { institutionId } = useInstitution()
  const [searchParams, setSearchParams] = useSearchParams()
  const form = useDialog<Laboratory>()
  const deactivateDialog = useDialog<Laboratory>()
  const deleteDialog = useDialog<Laboratory>()

  const search = searchParams.get('q') ?? ''
  const statusFilter = (searchParams.get('status') ?? 'all') as LabStatusFilter

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

  function setStatusFilter(value: LabStatusFilter) {
    setSearchParams(
      (prev) => {
        if (value === 'all') prev.delete('status')
        else prev.set('status', value)
        return prev
      },
      { replace: true },
    )
  }

  const { data: institution } = useQuery({
    queryKey: ['institution', institutionId],
    queryFn: () => getInstitution(institutionId ?? ''),
    enabled: !!institutionId,
  })

  const {
    data: laboratoriesData,
    isLoading,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: ['laboratories', debouncedSearch, statusFilter],
    queryFn: ({ pageParam }) =>
      listLaboratories({ status: statusFilter, search: debouncedSearch, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  })

  const laboratories = laboratoriesData?.pages.flatMap((p) => p.content) ?? []

  const activateMutation = useMutation({
    mutationFn: (lab: Laboratory) => activateLaboratory(lab.id),
    onSuccess: () => {
      refetch()
      toast.success('Laboratório ativado.')
    },
  })

  function handleSaved() {
    form.closeDialog()
    refetch()
  }

  function handleDeactivated() {
    deactivateDialog.closeDialog()
    refetch()
  }

  function handleDeleted() {
    deleteDialog.closeDialog()
    refetch()
  }

  const institutionName = institution?.acronym ?? institution?.name ?? ''
  const totalLabs = laboratoriesData?.pages[0]?.totalElements ?? 0

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-normal text-muted-foreground">
            Cadastro &rsaquo; Laboratórios
          </p>
          <h1 className="font-heading text-2xl font-bold">
            Laboratórios{institutionName ? ` — ${institutionName}` : ''}
          </h1>
        </div>
        <Button onClick={() => form.openDialog()}>
          <Plus className="size-4" />
          Novo Laboratório
        </Button>
      </div>

      <LaboratoryForm
        laboratory={form.data ?? undefined}
        open={form.open}
        onOpenChange={(open) => !open && form.closeDialog()}
        onSaved={handleSaved}
      />

      <div className="flex gap-4">
        <SummaryCard
          value={String(totalLabs)}
          label="Total de Laboratórios"
          icon={<Cpu className="size-[18px] text-primary-foreground" />}
          variant="accent"
        />
        <SummaryCard
          value="-"
          label="Estações Ativas"
          icon={<Monitor className="size-[18px] text-primary-foreground" />}
        />
        <SummaryCard
          value="-"
          label="Emissão Mensal Total"
          icon={<Leaf className="size-[18px] text-primary-foreground" />}
        />
      </div>

      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Buscar laboratório..."
            className="h-9 pl-8"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v as LabStatusFilter)}>
          <SelectTrigger className="w-auto min-w-36">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STATUS_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : laboratories.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum laboratório encontrado.</p>
      ) : (
        <div className="flex flex-col gap-4">
          {laboratories.map((laboratory) => (
            <LabCard
              key={laboratory.id}
              laboratory={laboratory}
              statusLabel={statusLabel(laboratory)}
              onEdit={(lab) => form.openDialog(lab)}
              onActivate={(lab) => activateMutation.mutate(lab)}
              onDeactivate={(lab) => deactivateDialog.openDialog(lab)}
              onDelete={(lab) => deleteDialog.openDialog(lab)}
            />
          ))}
        </div>
      )}

      <LoadMoreButton
        fetchNextPage={fetchNextPage}
        hasNextPage={hasNextPage}
        isFetchingNextPage={isFetchingNextPage}
      />

      {deactivateDialog.data && (
        <DeactivateDialog
          laboratory={deactivateDialog.data}
          open={deactivateDialog.open}
          onOpenChange={(open) => !open && deactivateDialog.closeDialog()}
          onDeactivated={handleDeactivated}
        />
      )}

      {deleteDialog.data && (
        <DeleteDialog
          laboratory={deleteDialog.data}
          open={deleteDialog.open}
          onOpenChange={(open) => !open && deleteDialog.closeDialog()}
          onDeleted={handleDeleted}
        />
      )}
    </div>
  )
}
