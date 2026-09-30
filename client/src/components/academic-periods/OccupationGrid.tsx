import { Moon, Sun, Sunset } from 'lucide-react'
import type React from 'react'
import { useMemo } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import type { Shift, ShiftType } from '@/lib/api/academic-periods'

const SHIFT_META: Record<ShiftType, { label: string; icon: typeof Sun; color: string }> = {
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

export type Occupancy = Record<string, Record<number, Set<number>>>

interface OccupationGridProps {
  shifts: Shift[]
  occupancy: Occupancy
  onToggle: (shiftId: string, dayOfWeek: number, slot: number) => void
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

function slotTimes(shift: Shift, slot: number) {
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

export const OccupationGrid: React.FC<OccupationGridProps> = ({ shifts, occupancy, onToggle }) => {
  const enabledShifts = shifts.filter((s) => s.enabled)

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

  return (
    <div className="rounded-lg border overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50">
            <th className="px-4 py-2 text-left font-medium min-w-[200px]">Aula</th>
            {allDays.map((d) => (
              <th key={d} className="px-3 py-2 text-center font-medium w-16">
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
            shiftTotal += shiftOcc[day]?.size ?? 0
          }
          const totalPossible = shift.classesPerDay * shift.activeDays.length

          return (
            <tbody key={shift.id}>
              {/* Shift header */}
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

              {/* Slot rows + break rows */}
              {Array.from({ length: shift.classesPerDay }, (_, i) => i + 1).flatMap((slot) => {
                const t = slotTimes(shift, slot)
                const slotCount = shift.activeDays.reduce(
                  (sum, day) => sum + (shiftOcc[day]?.has(slot) ? 1 : 0),
                  0,
                )

                const rows = [
                  <tr key={`s-${slot}`} className="border-b">
                    <td className="px-4 py-1.5">
                      <span className="text-xs text-muted-foreground">
                        {slot}ª aula · {t.start}–{t.end}
                      </span>
                    </td>
                    {allDays.map((day) => {
                      const isActive = shift.activeDays.includes(day)
                      const checked = shiftOcc[day]?.has(slot) ?? false
                      return (
                        <td key={day} className="px-3 py-1.5 text-center">
                          {isActive ? (
                            <Checkbox
                              checked={checked}
                              onCheckedChange={() => onToggle(shift.id, day, slot)}
                            />
                          ) : (
                            <span className="text-muted-foreground/30">—</span>
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

              {/* Shift subtotal */}
              <tr className="border-b bg-muted/30">
                <td className="px-4 py-1.5 text-xs font-medium text-muted-foreground">
                  Subtotal {meta.label}
                </td>
                {allDays.map((day) => {
                  const isActive = shift.activeDays.includes(day)
                  const count = shiftOcc[day]?.size ?? 0
                  const hours = (count * shift.classDurationMinutes) / 60
                  return (
                    <td key={day} className="px-3 py-1.5 text-center text-xs font-mono">
                      {isActive ? `${hours.toFixed(1)}h` : '—'}
                    </td>
                  )
                })}
                <td className="px-3 py-1.5 text-center text-xs font-mono font-semibold">
                  {((shiftTotal * shift.classDurationMinutes) / 60).toFixed(1)}h
                </td>
              </tr>
            </tbody>
          )
        })}

        <tfoot>
          <tr className="bg-muted/50 font-semibold">
            <td className="px-4 py-2 text-xs">Total por Dia</td>
            {allDays.map((day) => {
              let dayHours = 0
              for (const shift of enabledShifts) {
                if (shift.activeDays.includes(day)) {
                  dayHours +=
                    ((occupancy[shift.id]?.[day]?.size ?? 0) * shift.classDurationMinutes) / 60
                }
              }
              return (
                <td key={day} className="px-3 py-2 text-center text-xs font-mono">
                  {dayHours > 0 ? `${dayHours.toFixed(1)}h` : '—'}
                </td>
              )
            })}
            <td className="px-3 py-2 text-center text-xs font-mono">
              {(() => {
                let total = 0
                for (const shift of enabledShifts) {
                  const shiftOcc = occupancy[shift.id] ?? {}
                  for (const day of shift.activeDays) {
                    total += ((shiftOcc[day]?.size ?? 0) * shift.classDurationMinutes) / 60
                  }
                }
                return `${total.toFixed(1)}h`
              })()}
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
