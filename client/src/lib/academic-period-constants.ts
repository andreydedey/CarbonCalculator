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
