import type React from 'react'
import type { Shift, ShiftType } from '@/lib/api/academic-periods'

interface ShiftSummaryTableProps {
  shifts: Shift[]
}

const SHIFT_LABELS: Record<ShiftType, string> = {
  MORNING: 'Manhã',
  AFTERNOON: 'Tarde',
  EVENING: 'Noite',
}

const DAY_LABELS: Record<number, string> = {
  1: 'Seg',
  2: 'Ter',
  3: 'Qua',
  4: 'Qui',
  5: 'Sex',
  6: 'Sáb',
}

function formatTime(time: string): string {
  return time.substring(0, 5)
}

function formatActiveDays(days: number[]): string {
  if (days.length === 0) return '—'
  const sorted = [...days].sort((a, b) => a - b)

  const isConsecutive = sorted.every((d, i) => i === 0 || d === sorted[i - 1] + 1)
  if (isConsecutive && sorted.length > 1) {
    return `${DAY_LABELS[sorted[0]]} – ${DAY_LABELS[sorted[sorted.length - 1]]}`
  }
  return sorted.map((d) => DAY_LABELS[d]).join(', ')
}

export const ShiftSummaryTable: React.FC<ShiftSummaryTableProps> = ({ shifts }) => {
  const enabledShifts = shifts.filter((s) => s.enabled)

  if (enabledShifts.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic py-4 text-center">
        Nenhum turno configurado.
      </p>
    )
  }

  return (
    <div className="rounded-lg border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50">
            <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
              TURNO
            </th>
            <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
              HORÁRIO INÍCIO
            </th>
            <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
              FIM (CALCULADO)
            </th>
            <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
              AULAS/DIA
            </th>
            <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
              DURAÇÃO AULA
            </th>
            <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
              INTERVALO
            </th>
            <th className="px-4 py-2.5 text-left text-[11px] font-medium tracking-wide text-muted-foreground">
              DIAS ATIVOS
            </th>
          </tr>
        </thead>
        <tbody>
          {enabledShifts.map((shift) => (
            <tr key={shift.id} className="border-b last:border-0">
              <td className="px-4 py-2.5 font-medium">{SHIFT_LABELS[shift.shiftType]}</td>
              <td className="px-4 py-2.5 text-muted-foreground">{formatTime(shift.startTime)}</td>
              <td className="px-4 py-2.5 text-muted-foreground">{formatTime(shift.endTime)}</td>
              <td className="px-4 py-2.5 text-muted-foreground">{shift.classesPerDay}</td>
              <td className="px-4 py-2.5 text-muted-foreground">
                {shift.classDurationMinutes} min
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">
                {shift.breakDurationMinutes} min
              </td>
              <td className="px-4 py-2.5 text-muted-foreground">
                {formatActiveDays(shift.activeDays)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
