import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Info, Leaf, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
import { isApiError } from '@/lib/api/client'
import {
  type CreateEmissionFactorPayload,
  createEmissionFactor,
  deleteEmissionFactor,
  type EmissionFactor,
  listEmissionFactors,
  updateEmissionFactor,
} from '@/lib/api/emission-factors'
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

// Short month names for "Mmm/AAAA" format
const MONTH_SHORT = [
  '',
  'Jan',
  'Fev',
  'Mar',
  'Abr',
  'Mai',
  'Jun',
  'Jul',
  'Ago',
  'Set',
  'Out',
  'Nov',
  'Dez',
]

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = Array.from({ length: 10 }, (_, i) => CURRENT_YEAR - 5 + i)

function formatCompetencia(referenceMonth: string): string {
  const { year, month } = fromReferenceMonth(referenceMonth)
  return `${MONTH_SHORT[month]}/${year}`
}

function computeVariacao(rows: EmissionFactorRow[], referenceMonth: string): string {
  const existingRows = rows
    .filter((r) => r.factor !== null)
    .sort((a, b) => (a.referenceMonth < b.referenceMonth ? -1 : 1))
  const idx = existingRows.findIndex((r) => r.referenceMonth === referenceMonth)
  if (idx <= 0) return '—'
  const curr = existingRows[idx].factor!.value
  const prev = existingRows[idx - 1].factor!.value
  const pct = ((curr - prev) / prev) * 100
  const sign = pct < 0 ? '↓' : '↑'
  const color = pct < 0 ? 'text-[#24744D]' : 'text-[#DC2626]'
  return `${sign} ${Math.abs(pct).toFixed(1).replace('.', ',')}%|${color}`
}

function StatusBadge({ status }: { status: EmissionFactorRowStatus }) {
  if (status === 'em-uso') {
    return (
      <span className="inline-flex items-center rounded-full px-3 py-1 text-[11px] font-medium bg-[#DEECE2] text-[#1B5238]">
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
    <span className="inline-flex items-center rounded-full px-3 py-1 text-[11px] font-medium bg-[#DDE3DD] text-[#6D786D]">
      Anterior
    </span>
  )
}

type ModalState = null | { mode: 'add' } | { mode: 'edit'; factor: EmissionFactor }

export function EmissionFactorsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const yearParam = searchParams.get('year')
  const yearFilter = yearParam === 'all' ? undefined : yearParam ? Number(yearParam) : CURRENT_YEAR
  const [modal, setModal] = useState<ModalState>(null)

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

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<EmissionFactorFormValues>({
    resolver: zodResolver(emissionFactorFormSchema),
    defaultValues: { year: CURRENT_YEAR, month: 1, value: 0, source: '' },
  })

  const saveMutation = useMutation({
    mutationFn: (payload: CreateEmissionFactorPayload) =>
      modal?.mode === 'edit'
        ? updateEmissionFactor(modal.factor.id, payload)
        : createEmissionFactor(payload),
    onSuccess: () => {
      const wasEdit = modal?.mode === 'edit'
      closeModal()
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
    reset({ year: yearFilter ?? CURRENT_YEAR, month: 1, value: 0, source: '' })
    saveMutation.reset()
    setModal({ mode: 'add' })
  }

  function openEdit(factor: EmissionFactor) {
    const { year, month } = fromReferenceMonth(factor.referenceMonth)
    reset({ year, month, value: factor.value, source: factor.source })
    saveMutation.reset()
    setModal({ mode: 'edit', factor })
  }

  function openAddForMonth(referenceMonth: string) {
    const { year, month } = fromReferenceMonth(referenceMonth)
    reset({ year, month, value: 0, source: '' })
    saveMutation.reset()
    setModal({ mode: 'add' })
  }

  function closeModal() {
    reset()
    saveMutation.reset()
    setModal(null)
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
          <p className="text-xs text-[#6D786D]">Administração &rsaquo; Fatores de Emissão</p>
          <h1 className="font-heading text-2xl font-bold text-[#192219]">
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
      <div className="flex items-center gap-3 rounded-[10px] border border-[#C3E1D0] bg-[#DEECE2] px-5 py-4">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-[#24744D]">
          <Info className="size-[18px] text-white" />
        </div>
        <div className="flex flex-col gap-0.5">
          <p className="text-sm font-semibold text-[#192219]">
            Fator de emissão do Sistema Interligado Nacional (SIN)
          </p>
          <p className="text-[13px] text-[#24744D]">
            Os fatores são publicados mensalmente pelo MCTI com base nos dados do Operador Nacional
            do Sistema Elétrico (ONS). Tipo: Mensal (SIN).
          </p>
        </div>
      </div>

      {/* Add / edit modal */}
      <Dialog
        open={modal !== null}
        onOpenChange={(open) => {
          if (!open) closeModal()
        }}
      >
        <DialogContent className="sm:max-w-[440px]">
          <DialogHeader>
            <DialogTitle>
              {modal?.mode === 'edit' ? 'Editar fator de emissão' : 'Novo fator de emissão'}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4 pt-2">
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1.5">
                <Label>Ano</Label>
                <Select
                  value={watch('year')?.toString()}
                  onValueChange={(v) => setValue('year', Number(v), { shouldValidate: true })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Ano" />
                  </SelectTrigger>
                  <SelectContent>
                    {YEAR_OPTIONS.map((y) => (
                      <SelectItem key={y} value={y.toString()}>
                        {y}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.year && (
                  <span className="text-xs text-destructive">{errors.year.message}</span>
                )}
              </div>
              <div className="flex flex-col gap-1.5">
                <Label>Mês</Label>
                <Select
                  value={watch('month')?.toString()}
                  onValueChange={(v) => setValue('month', Number(v), { shouldValidate: true })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Mês" />
                  </SelectTrigger>
                  <SelectContent>
                    {MONTH_SHORT.slice(1).map((name, idx) => (
                      <SelectItem key={name} value={(idx + 1).toString()}>
                        {name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.month && (
                  <span className="text-xs text-destructive">{errors.month.message}</span>
                )}
              </div>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Valor (kgCO₂/kWh)</Label>
              <Input type="number" step="0.000001" placeholder="0.000000" {...register('value')} />
              {errors.value && (
                <span className="text-xs text-destructive">{errors.value.message}</span>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Fonte</Label>
              <Input placeholder="ex: MCTI — Fator médio SIN" {...register('source')} />
              {errors.source && (
                <span className="text-xs text-destructive">{errors.source.message}</span>
              )}
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={closeModal}>
                Cancelar
              </Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending
                  ? 'Salvando…'
                  : modal?.mode === 'edit'
                    ? 'Salvar'
                    : 'Cadastrar'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Hero card — fator em uso */}
      {activeRow?.factor && (
        <div className="flex items-center gap-6 rounded-[10px] border border-[#C3E1D0] bg-[#DEECE2] p-6">
          <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-[#24744D]">
            <Leaf className="size-6 text-white" />
          </div>
          <div className="flex-1 flex flex-col gap-1">
            <p className="text-[12px] font-medium tracking-wide text-[#6D786D] uppercase">
              Fator de emissão em uso — Sistema Interligado Nacional (SIN)
            </p>
            <p className="font-mono text-[28px] font-bold text-[#192219] leading-none">
              {activeRow.factor.value.toLocaleString('pt-BR', {
                minimumFractionDigits: 4,
                maximumFractionDigits: 6,
              })}{' '}
              <span className="text-base font-normal text-[#6D786D]">kgCO₂/kWh</span>
            </p>
            <p className="text-[13px] text-[#24744D]">
              Competência {formatCompetencia(activeRow.referenceMonth)} · Aplicado aos cálculos do
              período vigente
            </p>
          </div>
          <div className="flex flex-col gap-2 items-end text-right">
            <div className="flex items-center gap-2">
              <span className="text-[12px] text-[#6D786D]">Fonte:</span>
              <span className="text-[12px] font-semibold text-[#192219] max-w-[200px] truncate">
                {activeRow.factor.source}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[12px] text-[#6D786D]">Tipo:</span>
              <span className="text-[12px] font-semibold text-[#192219]">Mensal (SIN)</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[12px] text-[#6D786D]">Competência:</span>
              <span className="text-[12px] font-semibold text-[#192219]">
                {formatCompetencia(activeRow.referenceMonth)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Table card */}
      <div className="rounded-[10px] border border-[#DDE3DD] bg-white overflow-hidden">
        {/* Card header */}
        <div className="border-b border-[#DDE3DD] px-6 py-5 flex flex-col gap-1">
          <h2 className="text-base font-semibold text-[#192219]">
            Histórico de Fatores de Emissão — SIN
          </h2>
          <p className="text-[13px] text-[#6D786D]">
            Fatores mensais de emissão de CO₂ pela geração de energia elétrica no Brasil
          </p>
        </div>

        {isLoading ? (
          <div className="px-6 py-8 text-sm text-[#6D786D]">Carregando...</div>
        ) : rows.length === 0 ? (
          <div className="flex items-center justify-center px-6 py-8">
            <p className="text-sm italic text-[#6D786D]">
              Nenhum fator cadastrado{yearFilter ? ` para ${yearFilter}` : ''}.
            </p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="bg-[#EEF1EC] hover:bg-[#EEF1EC]">
                <TableHead className="w-[130px] px-6 text-[11px] font-medium uppercase tracking-wider text-[#6D786D]">
                  Competência
                </TableHead>
                <TableHead className="w-[200px] px-4 text-[11px] font-medium uppercase tracking-wider text-[#6D786D]">
                  Fator (kgCO₂/kWh)
                </TableHead>
                <TableHead className="w-[140px] px-4 text-[11px] font-medium uppercase tracking-wider text-[#6D786D]">
                  Variação
                </TableHead>
                <TableHead className="px-4 text-[11px] font-medium uppercase tracking-wider text-[#6D786D]">
                  Fonte
                </TableHead>
                <TableHead className="w-[120px] px-4 text-[11px] font-medium uppercase tracking-wider text-[#6D786D]">
                  Status
                </TableHead>
                <TableHead className="w-[96px] px-4 text-right text-[11px] font-medium uppercase tracking-wider text-[#6D786D]">
                  Ações
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row, idx) => {
                const isActive = row.status === 'em-uso'
                const variacao = row.factor ? computeVariacao(rows, row.referenceMonth) : null
                const [varText, varColor] = variacao ? variacao.split('|') : ['—', 'text-[#6D786D]']
                const isEven = idx % 2 === 0

                return (
                  <TableRow
                    key={row.referenceMonth}
                    className={[
                      'relative border-b border-[#DDE3DD] hover:bg-[#f5f7f4]',
                      isEven ? 'bg-white' : 'bg-[#FBFCF9]',
                      isActive ? 'border-l-[3px] border-l-[#24744D]' : '',
                    ]
                      .filter(Boolean)
                      .join(' ')}
                  >
                    <TableCell
                      className={`px-6 py-0 h-[52px] text-[13px] ${isActive ? 'font-bold text-[#192219]' : 'font-medium text-[#192219]'}`}
                    >
                      {formatCompetencia(row.referenceMonth)}
                    </TableCell>
                    <TableCell className="px-4 py-0 h-[52px]">
                      {row.factor ? (
                        <span
                          className={`font-mono text-[14px] text-[#192219] ${isActive ? 'font-semibold' : 'font-normal'}`}
                        >
                          {row.factor.value.toLocaleString('pt-BR', {
                            minimumFractionDigits: 4,
                            maximumFractionDigits: 6,
                          })}
                        </span>
                      ) : (
                        <span className="text-[13px] text-[#6D786D]">—</span>
                      )}
                    </TableCell>
                    <TableCell
                      className={`px-4 py-0 h-[52px] text-[13px] font-semibold ${varColor}`}
                    >
                      {varText}
                    </TableCell>
                    <TableCell className="px-4 py-0 h-[52px] max-w-[280px]">
                      <span className="block truncate text-[12px] text-[#6D786D]">
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
                              className="size-7 text-[#6D786D] hover:text-[#192219]"
                              onClick={() => openEdit(row.factor!)}
                              disabled={modal !== null}
                            >
                              <Pencil className="size-3.5" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="size-7 text-destructive"
                              onClick={() => deleteMutation.mutate(row.factor!.id)}
                              disabled={modal !== null}
                            >
                              <Trash2 className="size-3.5" />
                            </Button>
                          </>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs border-[#C3E1D0] text-[#24744D] hover:bg-[#DEECE2]"
                            onClick={() => openAddForMonth(row.referenceMonth)}
                            disabled={modal !== null}
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
