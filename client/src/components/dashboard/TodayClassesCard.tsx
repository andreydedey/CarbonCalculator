import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Plus, RotateCcw, X } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { SHIFT_META } from '@/components/academic-periods/OccupationGrid'
import { StatusBadge } from '@/components/academic-periods/OccurrencesView'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { AcademicPeriod } from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'
import type { Laboratory } from '@/lib/api/laboratories'
import {
  type DayClass,
  deleteOccurrence,
  getDayClasses,
  upsertOccurrence,
} from '@/lib/api/occupation'
import { addDays, formatDayMonth, isoDayOfWeek, toIsoDate } from '@/lib/utils/occupation'

const DAY_SHORT: Record<number, string> = {
  1: 'Seg',
  2: 'Ter',
  3: 'Qua',
  4: 'Qui',
  5: 'Sex',
  6: 'Sáb',
  7: 'Dom',
}

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
  const [draft, setDraft] = useState(String(value))
  const [synced, setSynced] = useState(value)
  // Reset the draft when the server value changes (no useEffect)
  if (synced !== value) {
    setSynced(value)
    setDraft(String(value))
  }

  const commit = () => {
    const parsed = Number(draft)
    const valid = Number.isInteger(parsed) && parsed >= 1 && (capacity <= 0 || parsed <= capacity)
    if (!valid) {
      setDraft(String(value))
      if (draft !== String(value)) toast.error(`Informe de 1 a ${capacity} estações.`)
      return
    }
    if (parsed !== value) onCommit(parsed)
  }

  return (
    <div
      className={`flex h-8 w-32 items-center gap-1.5 rounded-md border bg-background px-2 ${
        highlighted ? 'border-primary ring-1 ring-primary' : ''
      }`}
    >
      <input
        type="number"
        min={1}
        max={capacity > 0 ? capacity : undefined}
        disabled={disabled}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur()
        }}
        className="w-full bg-transparent font-mono text-[13px] font-semibold outline-none disabled:opacity-60"
        aria-label="Estações usadas"
      />
      <span className="shrink-0 text-xs text-muted-foreground">de {capacity}</span>
    </div>
  )
}

function ExtraClassForm({
  period,
  laboratories,
  onSubmit,
}: {
  period: AcademicPeriod
  laboratories: Laboratory[]
  onSubmit: (labId: string, shiftId: string, slot: number, stations: number) => void
}) {
  const shifts = period.shifts.filter((s) => s.enabled)
  const [labId, setLabId] = useState(laboratories[0]?.id ?? '')
  const [shiftId, setShiftId] = useState(shifts[0]?.id ?? '')
  const [slot, setSlot] = useState('1')
  const lab = laboratories.find((l) => l.id === labId)
  const shift = shifts.find((s) => s.id === shiftId)
  const [stations, setStations] = useState(String(lab?.totalStations ?? 1))
  const parsed = Number(stations)
  const capacity = lab?.totalStations ?? 0
  const valid =
    !!lab &&
    !!shift &&
    Number.isInteger(parsed) &&
    parsed >= 1 &&
    (capacity <= 0 || parsed <= capacity)

  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (valid) onSubmit(labId, shiftId, Number(slot), parsed)
      }}
    >
      <p className="text-sm font-semibold">Registrar aula extra</p>
      <Select value={labId} onValueChange={setLabId}>
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
      <div className="flex gap-2">
        <Select value={shiftId} onValueChange={setShiftId}>
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Turno" />
          </SelectTrigger>
          <SelectContent>
            {shifts.map((s) => (
              <SelectItem key={s.id} value={s.id}>
                {SHIFT_META[s.shiftType].label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={slot} onValueChange={setSlot}>
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
      <div className="flex items-center gap-2">
        <Input
          type="number"
          min={1}
          max={capacity > 0 ? capacity : undefined}
          value={stations}
          onChange={(e) => setStations(e.target.value)}
          className="font-mono"
          aria-label="Estações usadas"
        />
        <span className="shrink-0 text-xs text-muted-foreground">de {capacity} estações</span>
      </div>
      <Button type="submit" size="sm" disabled={!valid}>
        Registrar
      </Button>
    </form>
  )
}

export function TodayClassesCard({
  period,
  laboratories,
  canEdit,
}: {
  period: AcademicPeriod | null
  laboratories: Laboratory[]
  canEdit: boolean
}) {
  const queryClient = useQueryClient()
  const today = toIsoDate(new Date())
  const [date, setDate] = useState(today)
  const [labFilter, setLabFilter] = useState(ALL_LABS)
  const [extraOpen, setExtraOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['day-classes', date],
    queryFn: () => getDayClasses(date),
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['day-classes'] })
    queryClient.invalidateQueries({ queryKey: ['occurrences'] })
    queryClient.invalidateQueries({ queryKey: ['emissions'] })
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
    onSuccess: invalidate,
    onError,
  })

  const resetMutation = useMutation({
    mutationFn: (c: DayClass) =>
      deleteOccurrence(periodId as string, c.laboratoryId, {
        shiftId: c.shiftId,
        date,
        slot: c.slot,
      }),
    onSuccess: invalidate,
    onError,
  })

  const classes = (data?.classes ?? []).filter(
    (c) => labFilter === ALL_LABS || c.laboratoryId === labFilter,
  )
  const editable = canEdit && !!periodId
  const dayLabel = `${DAY_SHORT[isoDayOfWeek(date)]} ${formatDayMonth(date)}`
  const title = date === today ? `Aulas de hoje — ${dayLabel}` : `Aulas de ${dayLabel}`
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
          <Button
            variant={date === today ? 'secondary' : 'ghost'}
            size="sm"
            className="h-7"
            onClick={() => setDate(today)}
          >
            Hoje
          </Button>
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
            {classes.map((c) => (
              <tr
                key={`${c.laboratoryId}-${c.shiftId}-${c.slot}`}
                className="h-12 border-b last:border-0"
              >
                <td className="px-1 text-[13px] font-semibold">{c.laboratoryName}</td>
                <td className="text-[13px]">
                  {SHIFT_META[c.shiftType].label} · {c.slot}ª aula
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
                  onSubmit={(labId, shiftId, slot, stationsUsed) =>
                    saveMutation.mutate(
                      { labId, shiftId, slot, stationsUsed },
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
