// Pure helpers for the consumption source shown on the emissions screen. No '@/...' imports so
// node --test can load them.

const PART_LABELS: Record<string, string> = {
  measurement_combined: 'Medição conjunta',
  measurement_computer: 'Medição do computador',
  measurement_monitor: 'medição do monitor',
  specification_computer: 'Especificação do computador',
  specification_monitor: 'especificação do monitor',
  specification: 'Especificação',
}

/** "measurement_computer+specification_monitor" → "Medição do computador + especificação do monitor" */
export function consumptionSourceLabel(source: string): string {
  return source
    .split('+')
    .map((part) => PART_LABELS[part] ?? part)
    .join(' + ')
}

/** Whether any part of the consumption comes from the manufacturer's specification (an estimate). */
export function isEstimate(source: string): boolean {
  return source.split('+').some((part) => part.startsWith('specification'))
}
