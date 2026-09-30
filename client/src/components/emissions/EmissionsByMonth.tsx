import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { MonthEmission } from '@/lib/api/emissions'

interface EmissionsByMonthProps {
  data: MonthEmission[]
}

const MONTH_SHORT: Record<string, string> = {
  '01': 'Jan',
  '02': 'Fev',
  '03': 'Mar',
  '04': 'Abr',
  '05': 'Mai',
  '06': 'Jun',
  '07': 'Jul',
  '08': 'Ago',
  '09': 'Set',
  '10': 'Out',
  '11': 'Nov',
  '12': 'Dez',
}

function formatMonth(month: string) {
  const parts = month.split('-')
  return `${MONTH_SHORT[parts[1]] ?? parts[1]}/${parts[0].slice(2)}`
}

export function EmissionsByMonth({ data }: EmissionsByMonthProps) {
  const chartData = data.map((d) => ({
    month: formatMonth(d.month),
    emissionKg: d.emissionKg,
    energyKwh: d.energyKwh,
  }))

  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-semibold">Emissão por mês</h3>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="month" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} />
          <Tooltip
            formatter={(value: number) => [`${value.toFixed(2)} kgCO₂`, 'Emissão']}
            labelStyle={{ fontWeight: 600 }}
          />
          <Bar dataKey="emissionKg" fill="hsl(var(--chart-1))" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
