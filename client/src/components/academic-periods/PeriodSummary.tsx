import { useQuery } from '@tanstack/react-query'
import type React from 'react'
import { type PeriodSummary as PeriodSummaryType, getPeriodSummary } from '@/lib/api/academic-periods'

interface PeriodSummaryProps {
  periodId: string
}

export const PeriodSummary: React.FC<PeriodSummaryProps> = ({ periodId }) => {
  const { data: summary, isLoading } = useQuery({
    queryKey: ['period-summary', periodId],
    queryFn: () => getPeriodSummary(periodId),
  })

  if (isLoading) return <p className="text-sm text-muted-foreground">Calculando resumo...</p>

  if (!summary) return null

  const totalSchoolDays = summary.schoolDaysPerMonth.reduce((sum, m) => sum + m.schoolDays, 0)

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h3 className="text-sm font-semibold mb-3">Dias Letivos por Mês</h3>
        <div className="rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="px-4 py-2 text-left font-medium">Mês</th>
                <th className="px-4 py-2 text-right font-medium">Dias Letivos</th>
              </tr>
            </thead>
            <tbody>
              {summary.schoolDaysPerMonth.map((m) => (
                <tr key={m.month} className="border-b last:border-0">
                  <td className="px-4 py-2">{formatMonth(m.month)}</td>
                  <td className="px-4 py-2 text-right font-mono">{m.schoolDays}</td>
                </tr>
              ))}
              <tr className="bg-muted/30 font-semibold">
                <td className="px-4 py-2">Total</td>
                <td className="px-4 py-2 text-right font-mono">{totalSchoolDays}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {summary.laboratorySummaries.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold mb-3">Horas de Uso por Laboratório</h3>
          <div className="rounded-lg border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-2 text-left font-medium">Laboratório</th>
                  {summary.schoolDaysPerMonth.map((m) => (
                    <th key={m.month} className="px-4 py-2 text-right font-medium">
                      {shortMonth(m.month)}
                    </th>
                  ))}
                  <th className="px-4 py-2 text-right font-medium">Total</th>
                </tr>
              </thead>
              <tbody>
                {summary.laboratorySummaries.map((lab) => (
                  <tr key={lab.laboratoryId} className="border-b last:border-0">
                    <td className="px-4 py-2 font-medium">{lab.laboratoryName}</td>
                    {summary.schoolDaysPerMonth.map((m) => {
                      const monthData = lab.hoursPerMonth.find((h) => h.month === m.month)
                      return (
                        <td key={m.month} className="px-4 py-2 text-right font-mono text-xs">
                          {monthData ? monthData.hours.toFixed(0) : '0'}h
                        </td>
                      )
                    })}
                    <td className="px-4 py-2 text-right font-mono font-semibold">
                      {lab.totalHours.toFixed(0)}h
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}

function formatMonth(ym: string): string {
  const [year, month] = ym.split('-')
  const months = [
    '', 'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro',
  ]
  return `${months[Number(month)]} ${year}`
}

function shortMonth(ym: string): string {
  const [, month] = ym.split('-')
  const short = ['', 'Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']
  return short[Number(month)]
}
