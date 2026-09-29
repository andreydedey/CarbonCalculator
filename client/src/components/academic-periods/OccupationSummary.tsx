import { useQueries, useQuery } from '@tanstack/react-query'
import type React from 'react'
import { useMemo } from 'react'
import {
  getSchedule,
  type ScheduleEntry,
  type Shift,
  type ShiftType,
} from '@/lib/api/academic-periods'
import { type Laboratory, listLaboratories } from '@/lib/api/laboratories'

interface OccupationSummaryProps {
  periodId: string
  shifts: Shift[]
}

const SHIFT_ORDER: ShiftType[] = ['MORNING', 'AFTERNOON', 'EVENING']

const DAY_COLUMNS = [
  { day: 1, label: 'SEG' },
  { day: 2, label: 'TER' },
  { day: 3, label: 'QUA' },
  { day: 4, label: 'QUI' },
  { day: 5, label: 'SEX' },
  { day: 6, label: 'SÁB' },
]

function formatHoursMinutes(totalMinutes: number): string {
  const h = Math.floor(totalMinutes / 60)
  const m = totalMinutes % 60
  if (m === 0) return `${h}h`
  return `${h}h${String(m).padStart(2, '0')}`
}

function MiniStrip({
  shifts,
  entries,
  dayOfWeek,
}: {
  shifts: Shift[]
  entries: ScheduleEntry[]
  dayOfWeek: number
}) {
  const enabledShifts = shifts
    .filter((s) => s.enabled && s.activeDays.includes(dayOfWeek))
    .sort((a, b) => SHIFT_ORDER.indexOf(a.shiftType) - SHIFT_ORDER.indexOf(b.shiftType))

  if (enabledShifts.length === 0) return <span className="text-muted-foreground/30">—</span>

  return (
    <div className="flex items-center gap-1.5">
      {enabledShifts.map((shift) => {
        const entry = entries.find((e) => e.shiftId === shift.id && e.dayOfWeek === dayOfWeek)
        const occupied = new Set(entry?.occupiedSlots ?? [])

        return (
          <div key={shift.id} className="flex items-center gap-[2px]">
            {Array.from({ length: shift.classesPerDay }, (_, i) => i + 1).map((slot) => (
              <div
                key={slot}
                className={`h-[18px] w-[6px] rounded-[1px] ${
                  occupied.has(slot) ? 'bg-primary' : 'bg-[#E4E9E4]'
                }`}
              />
            ))}
          </div>
        )
      })}
    </div>
  )
}

function LabRow({
  lab,
  shifts,
  entries,
  isLoading,
}: {
  lab: Laboratory
  shifts: Shift[]
  entries: ScheduleEntry[]
  isLoading: boolean
}) {
  const enabledShifts = shifts.filter((s) => s.enabled)

  const { totalAulas, totalMinutes } = useMemo(() => {
    let aulas = 0
    let minutes = 0
    for (const entry of entries) {
      const shift = enabledShifts.find((s) => s.id === entry.shiftId)
      if (!shift) continue
      aulas += entry.occupiedSlots.length
      minutes += entry.occupiedSlots.length * shift.classDurationMinutes
    }
    return { totalAulas: aulas, totalMinutes: minutes }
  }, [entries, enabledShifts])

  if (isLoading) {
    return (
      <tr className="border-b last:border-0">
        <td className="px-4 py-2.5 font-medium text-sm">{lab.name}</td>
        <td colSpan={DAY_COLUMNS.length + 2} className="px-3 py-2.5 text-sm text-muted-foreground">
          Carregando...
        </td>
      </tr>
    )
  }

  return (
    <tr className="border-b last:border-0">
      <td className="px-4 py-2.5 font-medium text-sm">{lab.name}</td>
      {DAY_COLUMNS.map(({ day }) => (
        <td key={day} className="px-3 py-2.5">
          <MiniStrip shifts={shifts} entries={entries} dayOfWeek={day} />
        </td>
      ))}
      <td className="px-3 py-2.5 text-right text-sm font-medium">{totalAulas}</td>
      <td className="px-3 py-2.5 text-right text-sm font-medium">
        {formatHoursMinutes(totalMinutes)}
      </td>
    </tr>
  )
}

export const OccupationSummary: React.FC<OccupationSummaryProps> = ({ periodId, shifts }) => {
  const { data: labsPage } = useQuery({
    queryKey: ['laboratories', 'all'],
    queryFn: () => listLaboratories({ status: 'active', size: 100 }),
  })

  const laboratories = labsPage?.content ?? []

  const scheduleQueries = useQueries({
    queries: laboratories.map((lab) => ({
      queryKey: ['schedule', periodId, lab.id],
      queryFn: () => getSchedule(periodId, lab.id),
      enabled: !!periodId && !!lab.id,
    })),
  })

  const { grandTotalAulas, grandTotalMinutes } = useMemo(() => {
    const enabledShifts = shifts.filter((s) => s.enabled)
    let aulas = 0
    let minutes = 0
    for (const query of scheduleQueries) {
      if (!query.data) continue
      for (const entry of query.data) {
        const shift = enabledShifts.find((s) => s.id === entry.shiftId)
        if (!shift) continue
        aulas += entry.occupiedSlots.length
        minutes += entry.occupiedSlots.length * shift.classDurationMinutes
      }
    }
    return { grandTotalAulas: aulas, grandTotalMinutes: minutes }
  }, [scheduleQueries, shifts])

  if (laboratories.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic py-4 text-center">
        Nenhum laboratório ativo cadastrado.
      </p>
    )
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-lg border">
        <table className="w-full">
          <thead>
            <tr className="border-b bg-muted/50">
              <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
                LABORATÓRIO
              </th>
              {DAY_COLUMNS.map(({ day, label }) => (
                <th
                  key={day}
                  className="px-3 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground"
                >
                  {label}
                </th>
              ))}
              <th className="px-3 py-2.5 text-right text-[11px] font-medium tracking-wide text-muted-foreground">
                AULAS
              </th>
              <th className="px-3 py-2.5 text-right text-[11px] font-medium tracking-wide text-muted-foreground">
                HORAS/SEM
              </th>
            </tr>
          </thead>
          <tbody>
            {laboratories.map((lab, i) => (
              <LabRow
                key={lab.id}
                lab={lab}
                shifts={shifts}
                entries={scheduleQueries[i]?.data ?? []}
                isLoading={scheduleQueries[i]?.isLoading ?? false}
              />
            ))}
          </tbody>
        </table>
      </div>

      {/* Footer: legend + totals */}
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5">
            <div className="h-3 w-[6px] rounded-[1px] bg-primary" />
            <span>aula ocupada</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="h-3 w-[6px] rounded-[1px] bg-[#E4E9E4]" />
            <span>
              aula livre — cada traço é uma aula de{' '}
              {shifts.find((s) => s.enabled)?.classDurationMinutes ?? 50} min, agrupadas em Manhã ·
              Tarde · Noite
            </span>
          </div>
        </div>
        <span className="font-medium text-foreground">
          {grandTotalAulas} aulas/semana · {formatHoursMinutes(grandTotalMinutes)}/semana
        </span>
      </div>
    </div>
  )
}
