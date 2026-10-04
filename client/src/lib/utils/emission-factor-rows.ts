// Pure logic for US-035: detect gaps in emission factor coverage for a given year.
// No imports from client.ts (keeps this testable with the Node.js test runner).

export type EmissionFactor = {
  id: string
  referenceMonth: string
  value: number
  source: string
}

export type EmissionFactorRowStatus = 'pendente' | 'em-uso' | 'anterior'

export type EmissionFactorRow =
  | { status: 'pendente'; referenceMonth: string; factor: null }
  | { status: 'em-uso' | 'anterior'; referenceMonth: string; factor: EmissionFactor }

function toMonthKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  return `${year}-${month}`
}

/**
 * Computes the rows for the "Fatores de Emissão" table: existing factors
 * annotated with their status (em-uso/anterior) plus synthetic rows for
 * months of `year` that are already past (<= currentDate) but have no
 * factor registered yet (pendente).
 */
export function computeEmissionFactorRows(
  factors: EmissionFactor[],
  currentDate: Date,
  year: number,
): EmissionFactorRow[] {
  const currentMonthKey = toMonthKey(currentDate)

  const mostRecentUpToNow = factors.reduce<EmissionFactor | null>((best, f) => {
    if (f.referenceMonth > currentMonthKey) return best
    if (!best || f.referenceMonth > best.referenceMonth) return f
    return best
  }, null)

  const factorRows: EmissionFactorRow[] = factors.map((f) => ({
    status: mostRecentUpToNow && f.referenceMonth === mostRecentUpToNow.referenceMonth
      ? 'em-uso'
      : 'anterior',
    referenceMonth: f.referenceMonth,
    factor: f,
  }))

  const existingMonths = new Set(factors.map((f) => f.referenceMonth))
  const pendingRows: EmissionFactorRow[] = []
  for (let month = 1; month <= 12; month++) {
    const referenceMonth = `${year}-${String(month).padStart(2, '0')}`
    if (referenceMonth <= currentMonthKey && !existingMonths.has(referenceMonth)) {
      pendingRows.push({ status: 'pendente', referenceMonth, factor: null })
    }
  }

  return [...factorRows, ...pendingRows].sort((a, b) =>
    a.referenceMonth < b.referenceMonth ? 1 : a.referenceMonth > b.referenceMonth ? -1 : 0,
  )
}
