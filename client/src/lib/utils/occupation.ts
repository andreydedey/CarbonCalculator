// Pure helpers for the occupation screens. No imports from '@/...' so node --test can load them.

export type UsageTier = 'high' | 'medium' | 'low'

/** Darker cells for classes that use more of the laboratory. */
export function usageTier(stations: number, capacity: number): UsageTier {
  if (capacity <= 0) return 'high'
  const ratio = stations / capacity
  if (ratio >= 0.85) return 'high'
  if (ratio >= 0.6) return 'medium'
  return 'low'
}

export const USAGE_TIER_CLASSES: Record<UsageTier, string> = {
  high: 'bg-primary text-primary-foreground',
  medium: 'bg-[#7DB596] text-[#0F3D27]',
  low: 'bg-[#CBE3D5] text-[#1C5A3C]',
}

/** ISO date (yyyy-mm-dd) of a local date, without timezone shifts. */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function parseIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function addDays(iso: string, days: number): string {
  const date = parseIsoDate(iso)
  date.setDate(date.getDate() + days)
  return toIsoDate(date)
}

/** Monday of the week containing {@code iso}. */
export function startOfWeek(iso: string): string {
  const date = parseIsoDate(iso)
  const dayOfWeek = date.getDay() === 0 ? 7 : date.getDay()
  return addDays(iso, 1 - dayOfWeek)
}

/** ISO day of week: Monday = 1 … Sunday = 7. */
export function isoDayOfWeek(iso: string): number {
  const day = parseIsoDate(iso).getDay()
  return day === 0 ? 7 : day
}

/** "24/04" */
export function formatDayMonth(iso: string): string {
  const [, m, d] = iso.split('-')
  return `${d}/${m}`
}

/** "24/04/2025" */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

export function clampDate(iso: string, min: string, max: string): string {
  if (iso < min) return min
  if (iso > max) return max
  return iso
}

/** Station-hours of a set of classes: Σ stations × class duration. */
export function stationHours(stations: number[], classDurationMinutes: number): number {
  return (stations.reduce((sum, s) => sum + s, 0) * classDurationMinutes) / 60
}
