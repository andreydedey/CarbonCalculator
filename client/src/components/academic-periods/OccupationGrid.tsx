import { Moon, Sun, Sunset } from 'lucide-react'
import type React from 'react'
import { useId, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import type { Shift, ShiftType } from '@/lib/api/academic-periods'
import { USAGE_TIER_CLASSES, usageTier } from '@/lib/utils/occupation'

export const SHIFT_META: Record<ShiftType, { label: string; icon: typeof Sun; color: string }> = {
  MORNING: { label: 'Manhã', icon: Sun, color: 'text-amber-500' },
  AFTERNOON: { label: 'Tarde', icon: Sunset, color: 'text-orange-500' },
  EVENING: { label: 'Noite', icon: Moon, color: 'text-indigo-500' },
}

const DAY_LABELS: Record<number, string> = {
  1: 'Seg',
  2: 'Ter',
  3: 'Qua',
  4: 'Qui',
  5: 'Sex',
  6: 'Sáb',
}

const DAY_FULL: Record<number, string> = {
  1: 'Segunda',
  2: 'Terça',
  3: 'Quarta',
  4: 'Quinta',
  5: 'Sexta',
  6: 'Sábado',
}

const EVERY_DAY: Record<number, string> = {
  1: 'todas as segundas',
  2: 'todas as terças',
  3: 'todas as quartas',
  4: 'todas as quintas',
  5: 'todas as sextas',
  6: 'todos os sábados',
}

const EMPTY_DAY: Record<number, number> = {}

/** shiftId → dayOfWeek → slot → stations used */
export type Occupancy = Record<string, Record<number, Record<number, number>>>

interface OccupationGridProps {
  shifts: Shift[]
  occupancy: Occupancy
  capacity: number
  readOnly?: boolean
  onSetStations: (shiftId: string, dayOfWeek: number, slot: number, stations: number | null) => void
}

function parseTime(time: string): number {
  const [h, m] = time.substring(0, 5).split(':').map(Number)
  return h * 60 + m
}

function fmtMin(totalMinutes: number): string {
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

export function slotTimes(shift: Shift, slot: number) {
  const base = parseTime(shift.startTime)
  const start = base + (slot - 1) * (shift.classDurationMinutes + shift.breakDurationMinutes)
  return { start: fmtMin(start), end: fmtMin(start + shift.classDurationMinutes) }
}

function breakTimes(shift: Shift, afterSlot: number) {
  const base = parseTime(shift.startTime)
  const start =
    base +
    (afterSlot - 1) * (shift.classDurationMinutes + shift.breakDurationMinutes) +
    shift.classDurationMinutes
  return { start: fmtMin(start), end: fmtMin(start + shift.breakDurationMinutes) }
}

function formatStationHours(hours: number): string {
  return `${Math.round(hours).toLocaleString('pt-BR')} est.-h`
}

function StationsEditor({
  title,
  help,
  initial,
  capacity,
  occupied,
  onSave,
  onRemove,
  onClose,
}: {
  title: string
  help: string
  initial: number
  capacity: number
  occupied: boolean
  onSave: (stations: number) => void
  onRemove: () => void
  onClose: () => void
}) {
  const inputId = useId()
  const [value, setValue] = useState(String(initial))
  const parsed = Number(value)
  const max = capacity > 0 ? capacity : Number.POSITIVE_INFINITY
  const valid = Number.isInteger(parsed) && parsed >= 1 && parsed <= max

  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (valid) onSave(parsed)
      }}
    >
      <p className="text-sm font-semibold">{title}</p>
      <div className="flex flex-col gap-1.5">
        <label htmlFor={inputId} className="text-xs font-medium">
          Estações usadas
        </label>
        <div className="flex items-center gap-2">
          <Input
            id={inputId}
            autoFocus
            type="number"
            min={1}
            max={capacity > 0 ? capacity : undefined}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className="font-mono"
          />
          {capacity > 0 && (
            <span className="shrink-0 text-xs text-muted-foreground">de {capacity}</span>
          )}
        </div>
      </div>
      <p className="text-[11px] text-muted-foreground">{help}</p>
      <div className="flex items-center gap-2 border-t pt-3">
        {occupied && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-destructive"
            onClick={onRemove}
          >
            Remover aula
          </Button>
        )}
        <div className="ml-auto flex gap-2">
          <Button type="button" variant="outline" size="sm" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" size="sm" disabled={!valid}>
            Salvar
          </Button>
        </div>
      </div>
    </form>
  )
}

export const OccupationGrid: React.FC<OccupationGridProps> = ({
  shifts,
  occupancy,
  capacity,
  readOnly,
  onSetStations,
}) => {
  const enabledShifts = shifts.filter((s) => s.enabled)
  const [editing, setEditing] = useState<string | null>(null)
  const [lastValue, setLastValue] = useState<number | null>(null)

  const allDays = useMemo(() => {
    const daySet = new Set<number>()
    for (const shift of enabledShifts) {
      for (const day of shift.activeDays) daySet.add(day)
    }
    return Array.from(daySet).sort((a, b) => a - b)
  }, [enabledShifts])

  if (enabledShifts.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic py-4 text-center">
        Nenhum turno habilitado. Configure os turnos antes de definir a ocupação.
      </p>
    )
  }

  const dayStationHours = (shift: Shift, day: number) =>
    (Object.values(occupancy[shift.id]?.[day] ?? EMPTY_DAY).reduce((s, v) => s + v, 0) *
      shift.classDurationMinutes) /
    60

  return (
    <div className="rounded-lg border overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50">
            <th className="px-4 py-2 text-left font-medium min-w-[180px]">Aula</th>
            {allDays.map((d) => (
              <th key={d} className="px-2 py-2 text-center font-medium">
                {DAY_LABELS[d]}
              </th>
            ))}
            <th className="px-3 py-2 text-center font-medium w-16">Total</th>
          </tr>
        </thead>

        {enabledShifts.map((shift) => {
          const meta = SHIFT_META[shift.shiftType]
          const Icon = meta.icon
          const shiftOcc = occupancy[shift.id] ?? {}

          let shiftTotal = 0
          for (const day of shift.activeDays) {
            shiftTotal += Object.keys(shiftOcc[day] ?? {}).length
          }
          const totalPossible = shift.classesPerDay * shift.activeDays.length

          return (
            <tbody key={shift.id}>
              <tr className="bg-muted/30 border-b">
                <td colSpan={allDays.length + 2} className="px-4 py-2">
                  <div className="flex items-center gap-2">
                    <Icon className={`size-4 ${meta.color}`} />
                    <span className="font-semibold">{meta.label}</span>
                    <span className="text-xs text-muted-foreground">
                      {shift.startTime.substring(0, 5)} – {shift.endTime.substring(0, 5)}
                    </span>
                    <span className="text-xs text-muted-foreground ml-auto">
                      {shiftTotal} de {totalPossible} aulas
                    </span>
                  </div>
                </td>
              </tr>

              {Array.from({ length: shift.classesPerDay }, (_, i) => i + 1).flatMap((slot) => {
                const t = slotTimes(shift, slot)
                const slotCount = shift.activeDays.filter(
                  (day) => shiftOcc[day]?.[slot] != null,
                ).length

                const rows = [
                  <tr key={`s-${slot}`} className="border-b">
                    <td className="px-4 py-1.5">
                      <span className="text-xs text-muted-foreground">
                        {slot}ª aula · {t.start}–{t.end}
                      </span>
                    </td>
                    {allDays.map((day) => {
                      if (!shift.activeDays.includes(day)) {
                        return (
                          <td key={day} className="px-2 py-1.5 text-center">
                            <span className="text-muted-foreground/30">—</span>
                          </td>
                        )
                      }
                      const stations = shiftOcc[day]?.[slot]
                      const key = `${shift.id}-${day}-${slot}`
                      const cell = (
                        <button
                          type="button"
                          disabled={readOnly}
                          className={`h-7 w-full rounded-md font-mono text-[11px] font-semibold transition-colors ${
                            stations != null
                              ? USAGE_TIER_CLASSES[usageTier(stations, capacity)]
                              : 'border bg-card hover:bg-muted'
                          }`}
                          aria-label={`${DAY_FULL[day]}, ${slot}ª aula`}
                        >
                          {stations != null ? `${stations}/${capacity}` : ''}
                        </button>
                      )
                      return (
                        <td key={day} className="px-2 py-1.5 text-center">
                          {readOnly ? (
                            cell
                          ) : (
                            <Popover
                              open={editing === key}
                              onOpenChange={(open) => setEditing(open ? key : null)}
                            >
                              <PopoverTrigger asChild>{cell}</PopoverTrigger>
                              <PopoverContent className="w-80">
                                <StationsEditor
                                  title={`${DAY_FULL[day]} · ${meta.label} · ${slot}ª aula (${t.start}–${t.end})`}
                                  help={`Vale para ${EVERY_DAY[day]} do período.`}
                                  initial={stations ?? lastValue ?? Math.max(capacity, 1)}
                                  capacity={capacity}
                                  occupied={stations != null}
                                  onSave={(value) => {
                                    onSetStations(shift.id, day, slot, value)
                                    setLastValue(value)
                                    setEditing(null)
                                  }}
                                  onRemove={() => {
                                    onSetStations(shift.id, day, slot, null)
                                    setEditing(null)
                                  }}
                                  onClose={() => setEditing(null)}
                                />
                              </PopoverContent>
                            </Popover>
                          )}
                        </td>
                      )
                    })}
                    <td className="px-3 py-1.5 text-center font-mono text-xs text-muted-foreground">
                      {slotCount}
                    </td>
                  </tr>,
                ]

                if (slot < shift.classesPerDay && shift.breakDurationMinutes > 0) {
                  const b = breakTimes(shift, slot)
                  rows.push(
                    <tr key={`b-${slot}`} className="border-b bg-muted/20">
                      <td
                        colSpan={allDays.length + 2}
                        className="px-4 py-0.5 text-[11px] text-muted-foreground/60 italic"
                      >
                        intervalo {shift.breakDurationMinutes} min · {b.start}–{b.end}
                      </td>
                    </tr>,
                  )
                }

                return rows
              })}

              <tr className="border-b bg-muted/30">
                <td className="px-4 py-1.5 text-xs font-medium text-muted-foreground">
                  Subtotal {meta.label}
                </td>
                {allDays.map((day) => (
                  <td key={day} className="px-2 py-1.5 text-center text-xs font-mono">
                    {shift.activeDays.includes(day)
                      ? formatStationHours(dayStationHours(shift, day))
                      : '—'}
                  </td>
                ))}
                <td className="px-3 py-1.5 text-center text-xs font-mono font-semibold">
                  {formatStationHours(
                    shift.activeDays.reduce((s, day) => s + dayStationHours(shift, day), 0),
                  )}
                </td>
              </tr>
            </tbody>
          )
        })}

        <tfoot>
          <tr className="bg-muted/50 font-semibold">
            <td className="px-4 py-2 text-xs">Total por dia</td>
            {allDays.map((day) => {
              let classes = 0
              let hours = 0
              for (const shift of enabledShifts) {
                if (!shift.activeDays.includes(day)) continue
                classes += Object.keys(occupancy[shift.id]?.[day] ?? {}).length
                hours += dayStationHours(shift, day)
              }
              return (
                <td key={day} className="px-2 py-2 text-center text-xs font-mono">
                  {classes > 0 ? (
                    <div className="flex flex-col">
                      <span>{classes} aulas</span>
                      <span className="font-normal text-muted-foreground">
                        {formatStationHours(hours)}
                      </span>
                    </div>
                  ) : (
                    '—'
                  )}
                </td>
              )
            })}
            <td className="px-3 py-2 text-center text-xs font-mono">
              {formatStationHours(
                enabledShifts.reduce(
                  (sum, shift) =>
                    sum + shift.activeDays.reduce((s, day) => s + dayStationHours(shift, day), 0),
                  0,
                ),
              )}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
