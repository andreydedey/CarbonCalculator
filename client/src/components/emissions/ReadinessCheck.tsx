import { AlertTriangle } from 'lucide-react'
import type { ReadinessResult } from '@/lib/api/emissions'

interface ReadinessCheckProps {
  readiness: ReadinessResult
}

export function ReadinessCheck({ readiness }: ReadinessCheckProps) {
  if (readiness.ready && readiness.configurationsWithoutMonitor.length === 0) return null

  const isBlocking = !readiness.ready

  return (
    <div
      className={
        isBlocking
          ? 'flex flex-col gap-2 rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-950/30'
          : 'flex flex-col gap-2 rounded-lg border border-amber-200 bg-amber-50 p-4 dark:border-amber-900 dark:bg-amber-950/30'
      }
    >
      <div
        className={
          isBlocking
            ? 'flex items-center gap-2 text-red-700 dark:text-red-400'
            : 'flex items-center gap-2 text-amber-700 dark:text-amber-400'
        }
      >
        <AlertTriangle className="size-4" />
        <span className="text-sm font-medium">
          {isBlocking ? 'Pré-requisitos não atendidos' : 'Avisos'}
        </span>
      </div>
      <ul
        className={`text-xs space-y-1 ml-6 list-disc ${isBlocking ? 'text-red-700 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'}`}
      >
        {readiness.missingEmissionFactors.length > 0 && (
          <li>
            Fatores de emissão faltantes:{' '}
            <strong>{readiness.missingEmissionFactors.join(', ')}</strong>
          </li>
        )}
        {readiness.laboratoriesWithoutEquipment.length > 0 && (
          <li>
            Laboratórios sem equipamentos:{' '}
            <strong>{readiness.laboratoriesWithoutEquipment.join(', ')}</strong>
          </li>
        )}
        {readiness.laboratoriesWithoutSchedule.length > 0 && (
          <li>
            Laboratórios sem grade de ocupação:{' '}
            <strong>{readiness.laboratoriesWithoutSchedule.join(', ')}</strong>
          </li>
        )}
        {readiness.configurationsWithoutMonitor.map((c) => (
          <li key={c.configurationId}>
            {c.label} — sem monitor (labs: {c.laboratoryNames.join(', ')})
          </li>
        ))}
      </ul>
    </div>
  )
}
