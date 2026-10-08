import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ChevronDown, ChevronLeft, ChevronRight, Plus, RotateCcw, X } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { StatusBadge } from '@/components/academic-periods/OccurrencesView'
import { StationsField } from '@/components/academic-periods/StationsField'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { FieldError } from '@/components/ui/field-error'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { DAY_SHORT_LABELS, SHIFT_LABELS } from '@/lib/academic-period-constants'
import type { AcademicPeriod } from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'
import type { Laboratory } from '@/lib/api/laboratories'
import {
  type DayClass,
  deleteOccurrence,
  getDayClasses,
  upsertOccurrence,
} from '@/lib/api/occupation'
import {
  type ExtraClassFormValues,
  extraClassFormSchema,
  type StationsFormValues,
  stationsFormSchema,
} from '@/lib/schemas/occupationSchema'
import { addDays, formatDayMonth, isoDayOfWeek, toIsoDate } from '@/lib/utils/occupation'

const ALL_LABS = 'all'

function StationsInput({
  value,
  capacity,
  highlighted,
  disabled,
  onCommit,
}: {
  value: number
  capacity: number
  highlighted: boolean
  disabled: boolean
  onCommit: (stations: number) => void
}) {
  // `values` keeps the field in sync when the server value changes
  const { register, handleSubmit, reset } = useForm<StationsFormValues>({
    resolver: zodResolver(stationsFormSchema(capacity)),
    values: { stations: value },
  })

  const commit = handleSubmit(
    (values) => {
      if (values.stations !== value) onCommit(values.stations)
    },
    (errors) => {
      toast.error(errors.stations?.message ?? 'Número de estações inválido.')
      reset({ stations: value })
    },
  )

  return (
    <form
      onSubmit={commit}
      className={`flex h-8 w-32 items-center gap-1.5 rounded-md border bg-background px-2 ${
        highlighted ? 'border-primary ring-1 ring-primary' : ''
      }`}
    >
      <input
        type="number"
        min={1}
        max={capacity > 0 ? capacity : undefined}
        disabled={disabled}
        className="w-full bg-transparent font-mono text-[13px] font-semibold outline-none disabled:opacity-60"
        aria-label="Estações usadas"
        {...register('stations', { onBlur: () => commit() })}
      />
      <span className="shrink-0 text-xs text-muted-foreground">de {capacity}</span>
    </form>
  )
}

function ExtraClassForm({
  period,
  laboratories,
  onSubmit,
}: {
  period: AcademicPeriod
  laboratories: Laboratory[]
  onSubmit: (values: ExtraClassFormValues) => void
}) {
  const shifts = period.shifts.filter((s) => s.enabled)
  const capacityByLab = Object.fromEntries(laboratories.map((l) => [l.id, l.totalStations]))
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<ExtraClassFormValues>({
    resolver: zodResolver(extraClassFormSchema(capacityByLab)),
    defaultValues: {
      laboratoryId: laboratories[0]?.id ?? '',
      shiftId: shifts[0]?.id ?? '',
      slot: 1,
      stations: laboratories[0]?.totalStations ?? 1,
    },
  })
  const shift = shifts.find((s) => s.id === watch('shiftId'))

  return (
    <form className="flex flex-col gap-3" onSubmit={handleSubmit(onSubmit)}>
      <p className="text-sm font-semibold">Registrar aula extra</p>
      <Select value={watch('laboratoryId')} onValueChange={(v) => setValue('laboratoryId', v)}>
        <SelectTrigger className="w-full">
          <SelectValue placeholder="Laboratório" />
        </SelectTrigger>
        <SelectContent>
          {laboratories.map((l) => (
            <SelectItem key={l.id} value={l.id}>
              {l.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <FieldError message={errors.laboratoryId?.message} />
      <div className="flex gap-2">
        <Select value={watch('shiftId')} onValueChange={(v) => setValue('shiftId', v)}>
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Turno" />
          </SelectTrigger>
          <SelectContent>
            {shifts.map((s) => (
              <SelectItem key={s.id} value={s.id}>
                {SHIFT_LABELS[s.shiftType]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={String(watch('slot'))} onValueChange={(v) => setValue('slot', Number(v))}>
          <SelectTrigger className="w-28">
            <SelectValue placeholder="Aula" />
          </SelectTrigger>
          <SelectContent>
            {Array.from({ length: shift?.classesPerDay ?? 0 }, (_, i) => i + 1).map((n) => (
              <SelectItem key={n} value={String(n)}>
                {n}ª aula
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <StationsField
        registration={register('stations')}
        capacity={capacityByLab[watch('laboratoryId')] ?? 0}
        error={errors.stations?.message}
      />
      <Button type="submit" size="sm">
        Registrar
      </Button>
    </form>
  )
}

export function TodayClassesCard({
  period,
  laboratories,
  canEdit,
  onChanged,
}: {
  period: AcademicPeriod | null
  laboratories: Laboratory[]
  canEdit: boolean
  // Lets the page refresh what depends on the registered classes (e.g. realized emissions)
  onChanged?: () => void
}) {
  const today = toIsoDate(new Date())
  const [searchParams, setSearchParams] = useSearchParams()
  const dateParam = searchParams.get('date')
  const date = dateParam && /^\d{4}-\d{2}-\d{2}$/.test(dateParam) ? dateParam : today
  const labFilter = searchParams.get('lab') ?? ALL_LABS
  const [extraOpen, setExtraOpen] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const VISIBLE_LIMIT = 10

  const setParam = (key: string, value: string | null) =>
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      if (value == null) next.delete(key)
      else next.set(key, value)
      return next
    })
  const setDate = (value: string) => setParam('date', value === today ? null : value)
  const setLabFilter = (value: string) => setParam('lab', value === ALL_LABS ? null : value)

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['day-classes', date],
    queryFn: () => getDayClasses(date),
  })

  const onSaved = () => {
    refetch()
    onChanged?.()
  }
  const onError = (error: unknown) =>
    toast.error(isApiError(error) ? error.message : 'Não foi possível salvar a alteração.')

  const periodId = data?.periodId ?? null

  const saveMutation = useMutation({
    mutationFn: (p: { labId: string; shiftId: string; slot: number; stationsUsed: number }) =>
      upsertOccurrence(periodId as string, p.labId, {
        shiftId: p.shiftId,
        date,
        slot: p.slot,
        stationsUsed: p.stationsUsed,
      }),
    onSuccess: onSaved,
    onError,
  })

  const resetMutation = useMutation({
    mutationFn: (c: DayClass) =>
      deleteOccurrence(periodId as string, c.laboratoryId, {
        shiftId: c.shiftId,
        date,
        slot: c.slot,
      }),
    onSuccess: onSaved,
    onError,
  })

  const classes = (data?.classes ?? []).filter(
    (c) => labFilter === ALL_LABS || c.laboratoryId === labFilter,
  )
  const editable = canEdit && !!periodId
  const dayLabel = `${DAY_SHORT_LABELS[isoDayOfWeek(date)]} ${formatDayMonth(date)}`
  const title = date === today ? 'Aulas de hoje' : 'Aulas do dia'
  const labsOfPeriod = laboratories.filter((l) => l.active)

  return (
    <Card className="gap-4 p-6">
      <div className="flex items-center gap-4">
        <div className="flex flex-1 flex-col gap-1">
          <h2 className="text-[15px] font-semibold">{title}</h2>
          <p className="text-xs text-muted-foreground">
            Valores vêm da grade semanal. Ajuste apenas o que mudou no dia.
          </p>
        </div>
        {date !== today && (
          <Button variant="link" size="sm" className="h-7 px-0" onClick={() => setDate(today)}>
            Voltar para hoje
          </Button>
        )}
        <div className="flex items-center gap-1 rounded-lg border px-1.5 py-1">
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            aria-label="Dia anterior"
            onClick={() => setDate(addDays(date, -1))}
          >
            <ChevronLeft className="size-4" />
          </Button>
          <span className="min-w-20 text-center text-[13px] font-semibold text-primary">
            {dayLabel}
          </span>
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            aria-label="Próximo dia"
            onClick={() => setDate(addDays(date, 1))}
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
        <Select value={labFilter} onValueChange={setLabFilter}>
          <SelectTrigger className="w-52">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_LABS}>Todos os laboratórios</SelectItem>
            {labsOfPeriod.map((l) => (
              <SelectItem key={l.id} value={l.id}>
                {l.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando aulas...</p>
      ) : !data?.periodId ? (
        <p className="text-sm text-muted-foreground italic">Nenhum período letivo nesta data.</p>
      ) : !data.schoolDay ? (
        <p className="text-sm text-muted-foreground italic">
          Sem aulas nesta data{data.holidayName ? ` · ${data.holidayName}` : ''}.
        </p>
      ) : classes.length === 0 ? (
        <p className="text-sm text-muted-foreground italic">
          Nenhuma aula na grade para esta data.
        </p>
      ) : (
        <>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-[11px] tracking-wider text-muted-foreground">
                <th className="px-1 pb-2 font-medium">LABORATÓRIO</th>
                <th className="pb-2 font-medium">TURNO · AULA</th>
                <th className="pb-2 font-medium">HORÁRIO</th>
                <th className="pb-2 font-medium">ESTAÇÕES USADAS</th>
                <th className="pb-2 font-medium">STATUS</th>
                <th className="pb-2" />
              </tr>
            </thead>
            <tbody>
              {(expanded ? classes : classes.slice(0, VISIBLE_LIMIT)).map((c) => (
                <tr
                  key={`${c.laboratoryId}-${c.shiftId}-${c.slot}`}
                  className="h-12 border-b last:border-0"
                >
                  <td className="px-1 text-[13px] font-semibold">{c.laboratoryName}</td>
                  <td className="text-[13px]">
                    {SHIFT_LABELS[c.shiftType]} · {c.slot}ª aula
                  </td>
                  <td className="font-mono text-xs text-muted-foreground">
                    {c.startTime}–{c.endTime}
                  </td>
                  <td>
                    {c.status === 'CANCELLED' ? (
                      <span className="font-mono text-[13px] text-muted-foreground/60">
                        — de {c.capacity}
                      </span>
                    ) : (
                      <StationsInput
                        value={c.stationsUsed}
                        capacity={c.capacity}
                        highlighted={c.status === 'ADJUSTED'}
                        disabled={!editable}
                        onCommit={(stationsUsed) =>
                          saveMutation.mutate({
                            labId: c.laboratoryId,
                            shiftId: c.shiftId,
                            slot: c.slot,
                            stationsUsed,
                          })
                        }
                      />
                    )}
                  </td>
                  <td>
                    <StatusBadge status={c.status} />
                  </td>
                  <td className="text-right">
                    {editable &&
                      (c.status === 'GRID' ? (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-8"
                          aria-label="Cancelar aula"
                          onClick={() =>
                            saveMutation.mutate({
                              labId: c.laboratoryId,
                              shiftId: c.shiftId,
                              slot: c.slot,
                              stationsUsed: 0,
                            })
                          }
                        >
                          <X className="size-4 text-destructive" />
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="size-8"
                          aria-label={
                            c.status === 'EXTRA' ? 'Remover aula extra' : 'Voltar ao padrão da grade'
                          }
                          onClick={() => resetMutation.mutate(c)}
                        >
                          <RotateCcw className="size-4 text-muted-foreground" />
                        </Button>
                      ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {classes.length > VISIBLE_LIMIT && (
            <Button
              variant="ghost"
              size="sm"
              className="w-full text-muted-foreground"
              onClick={() => setExpanded(!expanded)}
            >
              <ChevronDown className={`size-4 transition-transform ${expanded ? 'rotate-180' : ''}`} />
              {expanded ? 'Mostrar menos' : `Ver mais ${classes.length - VISIBLE_LIMIT} aulas`}
            </Button>
          )}
        </>
      )}

      {data?.periodId && (
        <div className="flex items-center pt-1">
          {editable && data.schoolDay && period?.id === data.periodId && (
            <Popover open={extraOpen} onOpenChange={setExtraOpen}>
              <PopoverTrigger asChild>
                <Button variant="ghost" size="sm" className="text-primary">
                  <Plus className="size-4" />
                  Registrar aula extra
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-80">
                <ExtraClassForm
                  period={period}
                  laboratories={labsOfPeriod}
                  onSubmit={(values) =>
                    saveMutation.mutate(
                      {
                        labId: values.laboratoryId,
                        shiftId: values.shiftId,
                        slot: values.slot,
                        stationsUsed: values.stations,
                      },
                      { onSuccess: () => setExtraOpen(false) },
                    )
                  }
                />
              </PopoverContent>
            </Popover>
          )}
          <Link
            to={`/academic-periods/${data.periodId}/occupation?mode=occurrences`}
            className="ml-auto text-[13px] font-medium text-primary hover:underline"
          >
            Ver ocupação completa →
          </Link>
        </div>
      )}
    </Card>
  )
}
