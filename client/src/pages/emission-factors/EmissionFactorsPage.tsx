import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Info, Leaf, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { EmissionFactorFormDialog } from '@/components/emission-factors/EmissionFactorFormDialog'
import { Button } from '@/components/ui/button'
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { TableSkeleton } from '@/components/ui/skeletons'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useDialog } from '@/hooks/use-dialog'
import { isApiError } from '@/lib/api/client'
import {
  type CreateEmissionFactorPayload,
  createEmissionFactor,
  deleteEmissionFactor,
  type EmissionFactor,
  listEmissionFactors,
  updateEmissionFactor,
} from '@/lib/api/emission-factors'
import { MONTH_SHORT } from '@/lib/constants'
import {
  type EmissionFactorFormValues,
  emissionFactorFormSchema,
  fromReferenceMonth,
  toReferenceMonth,
} from '@/lib/schemas/emissionFactorSchema'
import {
  computeEmissionFactorRows,
  type EmissionFactorRow,
  type EmissionFactorRowStatus,
} from '@/lib/utils/emission-factor-rows'

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = Array.from({ length: 10 }, (_, i) => CURRENT_YEAR - 5 + i)

function formatPeriod(referenceMonth: string): string {
  const { year, month } = fromReferenceMonth(referenceMonth)
  return `${MONTH_SHORT[month]}/${year}`
}

function computeVariation(rows: EmissionFactorRow[], referenceMonth: string): string {
  const existingRows = rows
    .filter((r) => r.factor !== null)
    .sort((a, b) => (a.referenceMonth < b.referenceMonth ? -1 : 1))
  const idx = existingRows.findIndex((r) => r.referenceMonth === referenceMonth)
  if (idx <= 0) return '—'
  const curr = existingRows[idx].factor?.value
  const prev = existingRows[idx - 1].factor?.value
  const pct = ((curr - prev) / prev) * 100
  const sign = pct < 0 ? '↓' : '↑'
  const color = pct < 0 ? 'text-primary' : 'text-destructive'
  return `${sign} ${Math.abs(pct).toFixed(1).replace('.', ',')}%|${color}`
}

function StatusBadge({ status }: { status: EmissionFactorRowStatus }) {
  if (status === 'em-uso') {
    return (
      <span className="inline-flex items-center rounded-full px-3 py-1 text-[11px] font-medium bg-accent text-primary">
        Em uso
      </span>
    )
  }
  if (status === 'pendente') {
    return (
      <span className="inline-flex items-center rounded-full px-3 py-1 text-[11px] font-medium bg-amber-100 text-amber-700">
        Pendente
      </span>
    )
  }
  return (
    <span className="inline-flex items-center rounded-full px-3 py-1 text-[11px] font-medium bg-muted text-muted-foreground">
      Anterior
    </span>
  )
}

export function EmissionFactorsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const yearParam = searchParams.get('year')
  const yearFilter = yearParam === 'all' ? undefined : yearParam ? Number(yearParam) : CURRENT_YEAR
  const {
    open: modalOpen,
    data: editingFactor,
    openDialog,
    closeDialog,
  } = useDialog<EmissionFactor>()

  const {
    data: page,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ['emission-factors', yearFilter],
    queryFn: () => listEmissionFactors({ year: yearFilter, size: 100 }),
  })

  const factors = page?.content ?? []
  const rows = computeEmissionFactorRows(factors, new Date(), yearFilter ?? CURRENT_YEAR)
  const activeRow = rows.find((r) => r.status === 'em-uso')

  const form = useForm<EmissionFactorFormValues>({
    resolver: zodResolver(emissionFactorFormSchema),
    defaultValues: { year: CURRENT_YEAR, month: 1, value: 0, source: '' },
  })

  const saveMutation = useMutation({
    mutationFn: (payload: CreateEmissionFactorPayload) =>
      editingFactor !== null
        ? updateEmissionFactor(editingFactor.id, payload)
        : createEmissionFactor(payload),
    onSuccess: () => {
      const wasEdit = editingFactor !== null
      handleClose()
      refetch()
      toast.success(wasEdit ? 'Fator atualizado.' : 'Fator cadastrado.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar.')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteEmissionFactor,
    onSuccess: () => {
      refetch()
      toast.success('Fator removido.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível remover.')
    },
  })

  function handleYearChange(v: string) {
    setSearchParams(v === 'all' ? { year: 'all' } : { year: v })
  }

  function openAdd() {
    form.reset({ year: yearFilter ?? CURRENT_YEAR, month: 1, value: 0, source: '' })
    saveMutation.reset()
    openDialog(null)
  }

  function openEdit(factor: EmissionFactor) {
    const { year, month } = fromReferenceMonth(factor.referenceMonth)
    form.reset({ year, month, value: factor.value, source: factor.source })
    saveMutation.reset()
    openDialog(factor)
  }

  function openAddForMonth(referenceMonth: string) {
    const { year, month } = fromReferenceMonth(referenceMonth)
    form.reset({ year, month, value: 0, source: '' })
    saveMutation.reset()
    openDialog(null)
  }

  function handleClose() {
    form.reset()
    saveMutation.reset()
    closeDialog()
  }

  function onSubmit(values: EmissionFactorFormValues) {
    saveMutation.mutate({
      referenceMonth: toReferenceMonth(values.year, values.month),
      value: values.value,
      source: values.source,
    })
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-2xl font-bold text-foreground">
            Fatores de Emissão — MCTI/SIRENE
          </h1>
        </div>
        <div className="flex items-center gap-3">
          <Select value={yearFilter?.toString() ?? 'all'} onValueChange={handleYearChange}>
            <SelectTrigger className="w-32">
              <SelectValue placeholder="Ano" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos</SelectItem>
              {YEAR_OPTIONS.map((y) => (
                <SelectItem key={y} value={y.toString()}>
                  {y}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={openAdd}>
            <Plus className="size-4" />
            Novo Fator
          </Button>
        </div>
      </div>

      {/* Info banner — SIN context */}
      <div className="flex items-center gap-3 rounded-[10px] border border-primary/30 bg-accent px-5 py-4">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary">
          <Info className="size-[18px] text-primary-foreground" />
        </div>
        <div className="flex flex-col gap-0.5">
          <p className="text-sm font-semibold text-foreground">
            Fator de emissão do Sistema Interligado Nacional (SIN)
          </p>
          <p className="text-[13px] text-primary">
            Os fatores são publicados mensalmente pelo MCTI com base nos dados do Operador Nacional
            do Sistema Elétrico (ONS). Tipo: Mensal (SIN).
          </p>
        </div>
      </div>

      <EmissionFactorFormDialog
        open={modalOpen}
        onOpenChange={(open) => {
          if (!open) handleClose()
        }}
        form={form}
        onSubmit={onSubmit}
        isEditing={editingFactor !== null}
        isPending={saveMutation.isPending}
      />

      {/* Hero card — active factor */}
      {activeRow?.factor && (
        <div className="flex items-center gap-6 rounded-[10px] border border-primary/30 bg-accent p-6">
          <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-primary">
            <Leaf className="size-6 text-primary-foreground" />
          </div>
          <div className="flex-1 flex flex-col gap-1">
            <p className="text-[12px] font-medium tracking-wide text-muted-foreground uppercase">
              Fator de emissão em uso — Sistema Interligado Nacional (SIN)
            </p>
            <p className="font-mono text-[28px] font-bold text-foreground leading-none">
              {activeRow.factor.value.toLocaleString('pt-BR', {
                minimumFractionDigits: 4,
                maximumFractionDigits: 6,
              })}{' '}
              <span className="text-base font-normal text-muted-foreground">kgCO₂/kWh</span>
            </p>
            <p className="text-[13px] text-primary">
              Competência {formatPeriod(activeRow.referenceMonth)} · Aplicado aos cálculos do
              período vigente
            </p>
          </div>
          <div className="flex flex-col gap-2 items-end text-right">
            <div className="flex items-center gap-2">
              <span className="text-[12px] text-muted-foreground">Fonte:</span>
              <span className="text-[12px] font-semibold text-foreground max-w-[200px] truncate">
                {activeRow.factor.source}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[12px] text-muted-foreground">Tipo:</span>
              <span className="text-[12px] font-semibold text-foreground">Mensal (SIN)</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[12px] text-muted-foreground">Competência:</span>
              <span className="text-[12px] font-semibold text-foreground">
                {formatPeriod(activeRow.referenceMonth)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Official source card */}
      <div className="flex items-center gap-3 rounded-lg border border-primary/30 bg-accent px-5 py-4">
        <Info className="size-[18px] shrink-0 text-primary" />
        <div className="flex flex-col gap-0.5">
          <p className="text-[13px] font-semibold text-foreground">
            Fonte oficial: MCTI — Sistema de Registro Nacional de Emissões (SIRENE)
          </p>
          <p className="text-[12px] text-primary">
            Os fatores de emissão são atualizados anualmente pelo Ministério da Ciência, Tecnologia
            e Inovação.
          </p>
        </div>
      </div>

      {/* Table card */}
      <div className="rounded-[10px] border border-border bg-card overflow-hidden">
        {/* Card header */}
        <div className="border-b border-border px-6 py-5 flex flex-col gap-1">
          <h2 className="text-base font-semibold text-foreground">
            Histórico de Fatores de Emissão — SIN
          </h2>
          <p className="text-[13px] text-muted-foreground">
            Fatores mensais de emissão de CO₂ pela geração de energia elétrica no Brasil
          </p>
        </div>

        {isLoading ? (
          <div className="px-6 py-4">
            <TableSkeleton rows={6} columns={5} />
          </div>
        ) : rows.length === 0 ? (
          <div className="px-6 py-8">
            {yearParam !== null ? (
              <Empty>
                <EmptyHeader>
                  <EmptyMedia>
                    <Search className="size-6 text-muted-foreground" />
                  </EmptyMedia>
                  <EmptyTitle>Nenhum resultado encontrado</EmptyTitle>
                  <EmptyDescription>Tente ajustar o filtro de ano.</EmptyDescription>
                </EmptyHeader>
              </Empty>
            ) : (
              <Empty>
                <EmptyHeader>
                  <EmptyMedia>
                    <Leaf className="size-6 text-muted-foreground" />
                  </EmptyMedia>
                  <EmptyTitle>Nenhum fator de emissão cadastrado</EmptyTitle>
                  <EmptyDescription>
                    Comece cadastrando o primeiro fator de emissão.
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            )}
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="bg-muted hover:bg-muted">
                <TableHead className="w-[130px] px-6 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  Competência
                </TableHead>
                <TableHead className="w-[200px] px-4 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  Fator (kgCO₂/kWh)
                </TableHead>
                <TableHead className="w-[140px] px-4 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  Variação
                </TableHead>
                <TableHead className="px-4 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  Fonte
                </TableHead>
                <TableHead className="w-[120px] px-4 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  Status
                </TableHead>
                <TableHead className="w-[96px] px-4 text-right text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                  Ações
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row, idx) => {
                const isActive = row.status === 'em-uso'
                const variation = row.factor ? computeVariation(rows, row.referenceMonth) : null
                const [varText, varColor] = variation
                  ? variation.split('|')
                  : ['—', 'text-muted-foreground']
                const isEven = idx % 2 === 0

                return (
                  <TableRow
                    key={row.referenceMonth}
                    className={[
                      'relative border-b border-border hover:bg-muted/50',
                      isEven ? 'bg-card' : 'bg-background',
                      isActive ? 'border-l-[3px] border-l-primary' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    <TableCell
                      className={`px-6 py-0 h-[52px] text-[13px] ${isActive ? 'font-bold text-foreground' : 'font-medium text-foreground'}`}
                    >
                      {formatPeriod(row.referenceMonth)}
                    </TableCell>
                    <TableCell className="px-4 py-0 h-[52px]">
                      {row.factor ? (
                        <span
                          className={`font-mono text-[14px] text-foreground ${isActive ? 'font-semibold' : 'font-normal'}`}
                        >
                          {row.factor.value.toLocaleString('pt-BR', {
                            minimumFractionDigits: 4,
                            maximumFractionDigits: 6,
                          })}
                        </span>
                      ) : (
                        <span className="text-[13px] text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell
                      className={`px-4 py-0 h-[52px] text-[13px] font-semibold ${varColor}`}
                    >
                      {varText}
                    </TableCell>
                    <TableCell className="px-4 py-0 h-[52px] max-w-[280px]">
                      <span className="block truncate text-[12px] text-muted-foreground">
                        {row.factor ? row.factor.source : '—'}
                      </span>
                    </TableCell>
                    <TableCell className="px-4 py-0 h-[52px]">
                      <StatusBadge status={row.status} />
                    </TableCell>
                    <TableCell className="px-4 py-0 h-[52px] text-right">
                      <div className="flex items-center justify-end gap-1">
                        {row.factor ? (
                          <>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="size-7 text-muted-foreground hover:text-foreground"
                              onClick={() => openEdit(row.factor!)}
                              disabled={modalOpen}
                            >
                              <Pencil className="size-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="size-7 text-destructive"
                              onClick={() => deleteMutation.mutate(row.factor?.id)}
                              disabled={modalOpen}
                            >
                              <Trash2 className="size-3.5" />
                            </Button>
                          </>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs border-primary/30 text-primary hover:bg-accent"
                            onClick={() => openAddForMonth(row.referenceMonth)}
                            disabled={modalOpen}
                          >
                            Cadastrar
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  )
}
