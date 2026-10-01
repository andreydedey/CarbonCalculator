import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { MONTH_NAMES } from '@/lib/constants'
import {
  type EmissionFactorFormValues,
  emissionFactorFormSchema,
  fromReferenceMonth,
  toReferenceMonth,
} from '@/lib/schemas/emissionFactorSchema'
import {
  computeEmissionFactorRows,
  type EmissionFactorRowStatus,
} from '@/lib/utils/emission-factor-rows'

function StatusBadge({ status }: { status: EmissionFactorRowStatus }) {
  if (status === 'em-uso') {
    return (
      <Badge className="border-green-200 bg-green-50 text-green-700 dark:border-green-900 dark:bg-green-950/30 dark:text-green-400">
        Em uso
      </Badge>
    )
  }
  if (status === 'pendente') {
    return (
      <Badge className="border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-400">
        Pendente
      </Badge>
    )
  }
  return <Badge variant="secondary">Anterior</Badge>
}

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = Array.from({ length: 10 }, (_, i) => CURRENT_YEAR - 5 + i)

type EditingState = null | { mode: 'add' } | { mode: 'edit'; factor: EmissionFactor }

export function EmissionFactorsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const yearParam = searchParams.get('year')
  const yearFilter = yearParam === 'all' ? undefined : yearParam ? Number(yearParam) : CURRENT_YEAR
  const [editing, setEditing] = useState<EditingState>(null)

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
      editing?.mode === 'edit'
        ? updateEmissionFactor(editing.factor.id, payload)
        : createEmissionFactor(payload),
    onSuccess: () => {
      const wasEdit = editing?.mode === 'edit'
      closeForm()
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
    setEditing({ mode: 'add' })
  }

  function openEdit(factor: EmissionFactor) {
    const { year, month } = fromReferenceMonth(factor.referenceMonth)
    reset({ year, month, value: factor.value, source: factor.source })
    saveMutation.reset()
    setEditing({ mode: 'edit', factor })
  }

  function openAddForMonth(referenceMonth: string) {
    const { year, month } = fromReferenceMonth(referenceMonth)
    reset({ year, month, value: 0, source: '' })
    saveMutation.reset()
    setEditing({ mode: 'add' })
  }

  function closeForm() {
    reset()
    saveMutation.reset()
    setEditing(null)
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
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-normal text-muted-foreground">
            Configuração &rsaquo; Fatores de Emissão
          </p>
          <h1 className="font-heading text-2xl font-bold">Fatores de Emissão</h1>
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
          <Button onClick={openAdd} disabled={editing !== null}>
            <Plus className="size-4" />
            Novo Fator
          </Button>
        </div>
      </div>

      {editing && (
        <div className="rounded-lg border bg-muted/30 p-4 flex flex-col gap-3">
          <span className="text-sm font-medium">
            {editing.mode === 'add' ? 'Novo fator de emissão' : 'Editar fator'}
          </span>
          <div className="grid grid-cols-5 gap-3 items-start">
            <div className="flex flex-col gap-1">
              <Input type="number" placeholder="Ano" {...register('year')} />
              {errors.year && (
                <span className="text-xs text-destructive">{errors.year.message}</span>
              )}
            </div>
            <div className="flex flex-col gap-1">
              <Select
                value={watch('month')?.toString()}
                onValueChange={(v) => setValue('month', Number(v), { shouldValidate: true })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Mês" />
                </SelectTrigger>
                <SelectContent>
                  {MONTH_NAMES.slice(1).map((name, idx) => (
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
            <div className="flex flex-col gap-1">
              <Input
                type="number"
                step="0.000001"
                placeholder="Valor (kgCO₂/kWh)"
                {...register('value')}
              />
              {errors.value && (
                <span className="text-xs text-destructive">{errors.value.message}</span>
              )}
            </div>
            <div className="flex flex-col gap-1 col-span-2">
              <div className="flex gap-2">
                <Input
                  placeholder="Fonte (ex: MCTI — Fator médio SIN)"
                  className="flex-1"
                  {...register('source')}
                />
                <Button
                  type="button"
                  size="icon"
                  disabled={saveMutation.isPending}
                  onClick={handleSubmit(onSubmit)}
                >
                  <Check className="size-4" />
                </Button>
                <Button type="button" variant="ghost" size="icon" onClick={closeForm}>
                  <X className="size-4" />
                </Button>
              </div>
              {errors.source && (
                <span className="text-xs text-destructive">{errors.source.message}</span>
              )}
            </div>
          </div>
        </div>
      )}

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : rows.length === 0 ? (
        <div className="rounded-lg border">
          <div className="flex items-center justify-center px-4 py-8">
            <p className="text-sm text-muted-foreground italic">
              Nenhum fator de emissão cadastrado{yearFilter ? ` para ${yearFilter}` : ''}.
            </p>
          </div>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="px-4">Ano</TableHead>
              <TableHead className="px-4">Mês</TableHead>
              <TableHead className="px-4 text-right">Valor (kgCO₂/kWh)</TableHead>
              <TableHead className="px-4">Fonte</TableHead>
              <TableHead className="px-4">Status</TableHead>
              <TableHead className="px-4 text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => {
              const { year, month } = fromReferenceMonth(row.referenceMonth)
              return (
                <TableRow key={row.referenceMonth}>
                  <TableCell className="px-4 font-mono text-xs">{year}</TableCell>
                  <TableCell className="px-4">{MONTH_NAMES[month]}</TableCell>
                  <TableCell className="px-4 text-right font-mono text-xs">
                    {row.factor ? row.factor.value : '—'}
                  </TableCell>
                  <TableCell className="px-4 text-xs text-muted-foreground max-w-xs truncate">
                    {row.factor ? row.factor.source : '—'}
                  </TableCell>
                  <TableCell className="px-4">
                    <StatusBadge status={row.status} />
                  </TableCell>
                  <TableCell className="px-4 text-right">
                    <div className="flex items-center justify-end gap-1">
                      {row.factor ? (
                        <>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-7"
                            onClick={() => openEdit(row.factor)}
                            disabled={editing !== null}
                          >
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="size-7 text-destructive"
                            onClick={() => deleteMutation.mutate(row.factor.id)}
                            disabled={editing !== null}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => openAddForMonth(row.referenceMonth)}
                          disabled={editing !== null}
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
  )
}
