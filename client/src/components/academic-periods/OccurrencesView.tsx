import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Plus, Trash2, X } from 'lucide-react'
import type React from 'react'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { type Occupancy, SHIFT_META, slotTimes } from '@/components/academic-periods/OccupationGrid'
import { StationsField } from '@/components/academic-periods/StationsField'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { DAY_SHORT_LABELS } from '@/lib/academic-period-constants'
import { type AcademicPeriod, getHolidays, type Shift } from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'
import {
  type ClassOccurrence,
  type ClassSessionStatus,
  deleteOccurrence,
  listOccurrences,
  upsertOccurrence,
} from '@/lib/api/occupation'
import {
  type OccurrenceChoice,
  type OccurrenceFormValues,
  occurrenceFormSchema,
} from '@/lib/schemas/occupationSchema'
import {
  addDays,
  clampDate,
  formatDate,
  formatDayMonth,
  startOfWeek,
  toIsoDate,
  USAGE_TIER_CLASSES,
  usageTier,
} from '@/lib/utils/occupation'

export const STATUS_LABEL: Record<ClassSessionStatus, string> = {
  GRID: 'conforme grade',
  ADJUSTED: 'ajustada',
  CANCELLED: 'cancelada',
  EXTRA: 'extra',
}

export function StatusBadge({ status }: { status: ClassSessionStatus }) {
  const variant =
    status === 'CANCELLED' ? 'destructive' : status === 'GRID' ? 'secondary' : 'default'
  return <Badge variant={variant}>{STATUS_LABEL[status]}</Badge>
}

interface OccurrencesViewProps {
  period: AcademicPeriod
  laboratoryId: string
  laboratoryName: string
  capacity: number
  occupancy: Occupancy
  canEdit: boolean
  hasUnsavedGrid: boolean
}

function OccurrenceEditor({
  title,
  subtitle,
  gridStations,
  current,
  capacity,
  onSave,
  onResetToGrid,
  onClose,
}: {
  title: string
  subtitle: string
  gridStations: number | null
  current: ClassOccurrence | undefined
  capacity: number
  onSave: (stations: number) => void
  onResetToGrid: () => void
  onClose: () => void
}) {
  const isExtraSlot = gridStations == null
  const initialChoice: OccurrenceChoice = isExtraSlot
    ? 'different'
    : current == null
      ? 'grid'
      : current.stationsUsed === 0
        ? 'cancelled'
        : 'different'
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<OccurrenceFormValues>({
    resolver: zodResolver(occurrenceFormSchema(capacity)),
    defaultValues: {
      choice: initialChoice,
      stations:
        current && current.stationsUsed > 0 ? current.stationsUsed : (gridStations ?? capacity),
    },
  })
  const choice = watch('choice')

  function onSubmit(values: OccurrenceFormValues) {
    if (values.choice === 'grid') onResetToGrid()
    else if (values.choice === 'cancelled') onSave(0)
    else onSave(values.stations)
  }

  const option = (value: OccurrenceChoice, label: string) => (
    <label className="flex cursor-pointer items-center gap-2 text-sm">
      <input type="radio" value={value} className="accent-primary" {...register('choice')} />
      {label}
    </label>
  )

  return (
    <form className="flex flex-col gap-3" onSubmit={handleSubmit(onSubmit)}>
      <div className="flex flex-col gap-0.5">
        <p className="text-sm font-semibold">{title}</p>
        <p className="text-xs text-muted-foreground">{subtitle}</p>
      </div>

      {!isExtraSlot && option('grid', `Conforme a grade (${gridStations} de ${capacity} estações)`)}
      {!isExtraSlot && option('different', 'Estações diferentes')}
      {choice === 'different' && (
        <div className={isExtraSlot ? '' : 'pl-6'}>
          <StationsField
            autoFocus
            registration={register('stations')}
            capacity={capacity}
            error={errors.stations?.message}
            hint={
              isExtraSlot
                ? 'Aula fora da grade, só nesta data.'
                : 'Vale apenas para esta data. As demais semanas seguem a grade.'
            }
          />
        </div>
      )}
      {!isExtraSlot && option('cancelled', 'Aula cancelada')}

      <div className="flex items-center gap-2 border-t pt-3">
        {isExtraSlot && current && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-destructive"
            onClick={onResetToGrid}
          >
            Remover aula extra
          </Button>
        )}
        <div className="ml-auto flex gap-2">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" size="sm">
            Salvar
          </Button>
        </div>
      </div>
    </form>
  )
}

export const OccurrencesView: React.FC<OccurrencesViewProps> = ({
  period,
  laboratoryId,
  laboratoryName,
  capacity,
  occupancy,
  canEdit,
  hasUnsavedGrid,
}) => {
  const today = toIsoDate(new Date())
  const [searchParams, setSearchParams] = useSearchParams()
  const weekParam = searchParams.get('week')
  const weekStart = startOfWeek(
    clampDate(
      weekParam && /^\d{4}-\d{2}-\d{2}$/.test(weekParam) ? weekParam : today,
      period.startDate,
      period.endDate,
    ),
  )
  const setWeekStart = (week: string) =>
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('week', week)
      return next
    })
  const [editing, setEditing] = useState<string | null>(null)

  const { data: holidays = [] } = useQuery({
    queryKey: ['holidays', period.id],
    queryFn: () => getHolidays(period.id),
  })

  const { data: occurrences = [], refetch: refetchOccurrences } = useQuery({
    queryKey: ['occurrences', period.id, laboratoryId],
    queryFn: () => listOccurrences(period.id, { laboratoryId }),
  })

  const onError = (error: unknown) =>
    toast.error(isApiError(error) ? error.message : 'Não foi possível salvar a ocorrência.')

  const saveMutation = useMutation({
    mutationFn: (payload: { shiftId: string; date: string; slot: number; stationsUsed: number }) =>
      upsertOccurrence(period.id, laboratoryId, payload),
    onSuccess: () => {
      refetchOccurrences()
      setEditing(null)
      toast.success('Ocorrência salva.')
    },
    onError,
  })

  const removeMutation = useMutation({
    mutationFn: (params: { shiftId: string; date: string; slot: number }) =>
      deleteOccurrence(period.id, laboratoryId, params),
    onSuccess: () => {
      refetchOccurrences()
      setEditing(null)
      toast.success('A aula voltou a seguir a grade.')
    },
    onError,
  })

  const enabledShifts = period.shifts.filter((s) => s.enabled)
  const shiftsById = useMemo(() => new Map(period.shifts.map((s) => [s.id, s])), [period.shifts])
  const holidayByDate = useMemo(
    () => new Map(holidays.map((h) => [h.date, h.description])),
    [holidays],
  )
  const occurrenceByKey = useMemo(
    () => new Map(occurrences.map((o) => [`${o.date}|${o.shiftId}|${o.slot}`, o])),
    [occurrences],
  )

  const days = useMemo(() => {
    const set = new Set<number>()
    for (const shift of enabledShifts) for (const d of shift.activeDays) set.add(d)
    return Array.from(set)
      .sort((a, b) => a - b)
      .map((day) => ({ day, date: addDays(weekStart, day - 1) }))
  }, [enabledShifts, weekStart])

  const counts = useMemo(() => {
    const result = { CANCELLED: 0, ADJUSTED: 0, EXTRA: 0 }
    for (const o of occurrences) if (o.status !== 'GRID') result[o.status] += 1
    return result
  }, [occurrences])

  const dateOutside = (date: string) => date < period.startDate || date > period.endDate

  const renderCell = (shift: Shift, day: number, date: string, slot: number) => {
    const meta = SHIFT_META[shift.shiftType]
    const t = slotTimes(shift, slot)
    const holiday = holidayByDate.get(date)
    if (!shift.activeDays.includes(day) || holiday || dateOutside(date)) {
      return <span className="text-muted-foreground/30">—</span>
    }

    const gridStations = occupancy[shift.id]?.[day]?.[slot] ?? null
    const occurrence = occurrenceByKey.get(`${date}|${shift.id}|${slot}`)
    const status: ClassSessionStatus | null = occurrence
      ? occurrence.status
      : gridStations != null
        ? 'GRID'
        : null
    const stations = occurrence ? occurrence.stationsUsed : gridStations

    let className = 'border bg-card hover:bg-muted'
    let content: React.ReactNode = ''
    if (status === 'GRID' && stations != null) {
      className = USAGE_TIER_CLASSES[usageTier(stations, capacity)]
      content = `${stations}/${capacity}`
    } else if (status === 'ADJUSTED' && stations != null) {
      className = `${USAGE_TIER_CLASSES[usageTier(stations, capacity)]} ring-2 ring-primary ring-inset`
      content = (
        <span className="inline-flex items-center gap-1">
          <span className="size-1.5 rounded-full bg-primary" />
          {stations}/{capacity}
        </span>
      )
    } else if (status === 'CANCELLED') {
      className = 'border border-destructive/60 bg-card text-destructive'
      content = <X className="mx-auto size-3.5" />
    } else if (status === 'EXTRA') {
      className = 'border border-primary bg-primary/10 text-primary'
      content = (
        <span className="inline-flex items-center gap-0.5">
          <Plus className="size-3" />
          {stations}/{capacity}
        </span>
      )
    }

    const key = `${date}|${shift.id}|${slot}`
    const button = (
      <button
        type="button"
        disabled={!canEdit}
        className={`h-7 w-full rounded-md font-mono text-[11px] font-semibold transition-colors ${className}`}
        aria-label={`${formatDate(date)}, ${meta.label}, ${slot}ª aula`}
      >
        {content}
      </button>
    )
    if (!canEdit) return button

    return (
      <Popover open={editing === key} onOpenChange={(open) => setEditing(open ? key : null)}>
        <PopoverTrigger asChild>{button}</PopoverTrigger>
        <PopoverContent className="w-80">
          <OccurrenceEditor
            title={`${DAY_SHORT_LABELS[day]} ${formatDayMonth(date)} · ${meta.label} · ${slot}ª aula (${t.start}–${t.end})`}
            subtitle={
              gridStations != null
                ? `${laboratoryName} · Padrão da grade: ${gridStations} de ${capacity} estações`
                : `${laboratoryName} · Horário livre na grade`
            }
            gridStations={gridStations}
            current={occurrence}
            capacity={capacity}
            onSave={(stationsUsed) =>
              saveMutation.mutate({ shiftId: shift.id, date, slot, stationsUsed })
            }
            onResetToGrid={() => removeMutation.mutate({ shiftId: shift.id, date, slot })}
            onClose={() => setEditing(null)}
          />
        </PopoverContent>
      </Popover>
    )
  }

  const weekEnd = addDays(weekStart, 4)

  return (
    <div className="flex flex-col gap-6">
      {hasUnsavedGrid && (
        <p className="rounded-md border border-amber-300 bg-amber-50 px-4 py-2 text-xs text-amber-900">
          A grade semanal tem alterações não salvas. As ocorrências usam a grade salva.
        </p>
      )}

      <div className="grid grid-cols-3 gap-4">
        {(
          [
            ['Aulas canceladas', counts.CANCELLED, 'neste período'],
            ['Aulas ajustadas', counts.ADJUSTED, 'estações diferentes do padrão'],
            ['Aulas extras', counts.EXTRA, 'fora da grade semanal'],
          ] as const
        ).map(([label, value, hint]) => (
          <Card key={label} size="sm">
            <CardHeader>
              <CardTitle className="text-sm">{label}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-2xl font-bold">{value}</p>
              <p className="text-xs text-muted-foreground">{hint}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="rounded-lg border overflow-hidden">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <div className="flex flex-col">
            <span className="text-sm font-semibold">Ocorrências por data — {laboratoryName}</span>
            <span className="text-xs text-muted-foreground">
              Sem ajuste, cada aula segue a grade semanal. Clique para registrar o que mudou na
              data.
            </span>
          </div>
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Semana anterior"
              disabled={weekStart <= startOfWeek(period.startDate)}
              onClick={() => setWeekStart(addDays(weekStart, -7))}
            >
              <ChevronLeft className="size-4" />
            </Button>
            <span className="min-w-44 text-center text-sm font-medium">
              Semana de {formatDayMonth(weekStart)} a {formatDate(weekEnd)}
            </span>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Próxima semana"
              disabled={addDays(weekStart, 7) > period.endDate}
              onClick={() => setWeekStart(addDays(weekStart, 7))}
            >
              <ChevronRight className="size-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() =>
                setWeekStart(startOfWeek(clampDate(today, period.startDate, period.endDate)))
              }
            >
              Hoje
            </Button>
          </div>
        </div>

        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-muted/50">
              <th className="px-4 py-2 text-left font-medium min-w-[180px]">Aula</th>
              {days.map(({ day, date }) => {
                const holiday = holidayByDate.get(date)
                return (
                  <th
                    key={day}
                    className={`px-2 py-2 text-center font-medium ${holiday ? 'bg-muted' : ''}`}
                  >
                    <div className="flex flex-col">
                      <span>
                        {DAY_SHORT_LABELS[day]} {formatDayMonth(date)}
                      </span>
                      {holiday ? (
                        <span className="text-[10px] font-normal text-muted-foreground">
                          {holiday}
                        </span>
                      ) : date === today ? (
                        <span className="text-[10px] font-semibold text-primary">hoje</span>
                      ) : null}
                    </div>
                  </th>
                )
              })}
            </tr>
          </thead>
          {enabledShifts.map((shift) => {
            const meta = SHIFT_META[shift.shiftType]
            const Icon = meta.icon
            return (
              <tbody key={shift.id}>
                <tr className="bg-muted/30 border-b">
                  <td colSpan={days.length + 1} className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <Icon className={`size-4 ${meta.color}`} />
                      <span className="font-semibold">{meta.label}</span>
                    </div>
                  </td>
                </tr>
                {Array.from({ length: shift.classesPerDay }, (_, i) => i + 1).map((slot) => {
                  const t = slotTimes(shift, slot)
                  return (
                    <tr key={slot} className="border-b">
                      <td className="px-4 py-1.5 text-xs text-muted-foreground">
                        {slot}ª aula · {t.start}–{t.end}
                      </td>
                      {days.map(({ day, date }) => (
                        <td key={day} className="px-2 py-1.5 text-center">
                          {renderCell(shift, day, date, slot)}
                        </td>
                      ))}
                    </tr>
                  )
                })}
              </tbody>
            )
          })}
        </table>

        <div className="flex flex-wrap items-center gap-4 border-t px-4 py-2.5 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1.5">
            <span className="h-3 w-5 rounded-sm bg-primary" /> conforme a grade
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="h-3 w-5 rounded-sm bg-[#CBE3D5] ring-2 ring-primary ring-inset" />{' '}
            ajustada
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="h-3 w-5 rounded-sm border border-destructive/60" /> cancelada
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="h-3 w-5 rounded-sm border border-primary bg-primary/10" /> extra
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="h-3 w-5 rounded-sm bg-muted" /> feriado
          </span>
        </div>
      </div>

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-1 border-b px-6 py-4">
          <h2 className="text-base font-semibold">Exceções registradas neste período</h2>
          <p className="text-[13px] text-muted-foreground">
            {occurrences.length} ajustes sobre a grade semanal · {period.name} · {laboratoryName}
          </p>
        </div>
        {occurrences.length === 0 ? (
          <p className="px-6 py-4 text-sm text-muted-foreground italic">
            Nenhuma exceção registrada. Todas as aulas seguem a grade semanal.
          </p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="bg-muted hover:bg-muted">
                <TableHead className="px-6 text-xs tracking-wider text-muted-foreground">
                  DATA
                </TableHead>
                <TableHead className="text-xs tracking-wider text-muted-foreground">AULA</TableHead>
                <TableHead className="text-xs tracking-wider text-muted-foreground">TIPO</TableHead>
                <TableHead className="text-xs tracking-wider text-muted-foreground">
                  PADRÃO → REGISTRADO
                </TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {occurrences.map((o) => {
                const shift = shiftsById.get(o.shiftId)
                const t = shift ? slotTimes(shift, o.slot) : null
                return (
                  <TableRow key={o.id}>
                    <TableCell className="px-6 font-mono text-[13px]">
                      {formatDate(o.date)}
                    </TableCell>
                    <TableCell className="text-[13px]">
                      {SHIFT_META[o.shiftType].label} · {o.slot}ª aula
                      {t ? ` (${t.start}–${t.end})` : ''}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={o.status} />
                    </TableCell>
                    <TableCell className="font-mono text-[13px]">
                      {o.gridStations ?? '—'} → {o.stationsUsed}
                    </TableCell>
                    <TableCell className="pr-6 text-right">
                      {canEdit && (
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label="Remover exceção"
                          onClick={() =>
                            removeMutation.mutate({
                              shiftId: o.shiftId,
                              date: o.date,
                              slot: o.slot,
                            })
                          }
                        >
                          <Trash2 className="size-4 text-destructive" />
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  )
}
