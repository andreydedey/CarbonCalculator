import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type {
  DayOfWeekEmission,
  EquipmentModelEmission,
  MonitorModelEmission,
  OperatingSystemEmission,
  ShiftEmission,
} from '@/lib/api/emissions'

const SHIFT_LABELS: Record<string, string> = {
  MORNING: 'Manhã',
  AFTERNOON: 'Tarde',
  EVENING: 'Noite',
}

interface BreakdownCardsProps {
  byShift: ShiftEmission[]
  byDayOfWeek: DayOfWeekEmission[]
  byEquipmentModel: EquipmentModelEmission[]
  byMonitorModel: MonitorModelEmission[]
  byOperatingSystem: OperatingSystemEmission[]
}

function RankingList({ items }: { items: { label: string; value: string; pct?: string }[] }) {
  return (
    <ul className="space-y-1.5">
      {items.map((item) => (
        <li key={item.label} className="flex items-center justify-between text-xs">
          <span className="truncate mr-2">{item.label}</span>
          <span className="font-mono text-muted-foreground whitespace-nowrap">
            {item.value}
            {item.pct && <span className="ml-1 text-muted-foreground/60">({item.pct})</span>}
          </span>
        </li>
      ))}
    </ul>
  )
}

export function BreakdownCards({
  byShift,
  byDayOfWeek,
  byEquipmentModel,
  byMonitorModel,
  byOperatingSystem,
}: BreakdownCardsProps) {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Card size="sm">
        <CardHeader>
          <CardTitle className="text-sm">Por turno</CardTitle>
        </CardHeader>
        <CardContent>
          <RankingList
            items={byShift.map((s) => ({
              label: SHIFT_LABELS[s.shiftType] ?? s.shiftType,
              value: `${s.emissionKg.toFixed(2)} kg`,
            }))}
          />
        </CardContent>
      </Card>

      <Card size="sm">
        <CardHeader>
          <CardTitle className="text-sm">Por dia da semana</CardTitle>
        </CardHeader>
        <CardContent>
          <RankingList
            items={byDayOfWeek.map((d) => ({
              label: d.label,
              value: `${d.emissionKg.toFixed(2)} kg`,
            }))}
          />
        </CardContent>
      </Card>

      <Card size="sm">
        <CardHeader>
          <CardTitle className="text-sm">Top modelos de computador</CardTitle>
        </CardHeader>
        <CardContent>
          <RankingList
            items={byEquipmentModel.map((m) => ({
              label: m.modelName,
              value: `${m.emissionKg.toFixed(2)} kg`,
              pct: `${m.percentage.toFixed(1)}%`,
            }))}
          />
        </CardContent>
      </Card>

      <Card size="sm">
        <CardHeader>
          <CardTitle className="text-sm">Top monitores</CardTitle>
        </CardHeader>
        <CardContent>
          {byMonitorModel.length > 0 ? (
            <RankingList
              items={byMonitorModel.map((m) => ({
                label: m.monitorName,
                value: `${m.emissionKg.toFixed(2)} kg`,
                pct: `${m.percentage.toFixed(1)}%`,
              }))}
            />
          ) : (
            <p className="text-xs text-muted-foreground italic">Sem dados de monitores</p>
          )}
        </CardContent>
      </Card>

      <Card size="sm" className="col-span-2">
        <CardHeader>
          <CardTitle className="text-sm">Por sistema operacional</CardTitle>
        </CardHeader>
        <CardContent>
          <RankingList
            items={byOperatingSystem.map((o) => ({
              label: o.operatingSystem,
              value: `${o.emissionKg.toFixed(2)} kg`,
              pct: `${o.percentage.toFixed(1)}%`,
            }))}
          />
        </CardContent>
      </Card>
    </div>
  )
}
