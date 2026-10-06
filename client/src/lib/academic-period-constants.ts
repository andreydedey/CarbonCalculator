import type { ShiftType } from '@/lib/api/academic-periods'

export const SHIFT_ORDER: ShiftType[] = ['MORNING', 'AFTERNOON', 'EVENING']

export const SHIFT_LABELS: Record<ShiftType, string> = {
  MORNING: 'Manhã',
  AFTERNOON: 'Tarde',
  EVENING: 'Noite',
}

export const DAY_OPTIONS = [
  { value: 1, label: 'Seg' },
  { value: 2, label: 'Ter' },
  { value: 3, label: 'Qua' },
  { value: 4, label: 'Qui' },
  { value: 5, label: 'Sex' },
  { value: 6, label: 'Sáb' },
] as const

/** ISO day of week (Monday = 1 … Sunday = 7) → "Seg", "Ter", … */
export const DAY_SHORT_LABELS: Record<number, string> = {
  ...Object.fromEntries(DAY_OPTIONS.map((d) => [d.value, d.label])),
  7: 'Dom',
}

/** ISO day of week (Monday = 1 … Sunday = 7) → "Segunda", "Terça", … */
export const DAY_FULL_LABELS: Record<number, string> = {
  1: 'Segunda',
  2: 'Terça',
  3: 'Quarta',
  4: 'Quinta',
  5: 'Sexta',
  6: 'Sábado',
  7: 'Domingo',
}
