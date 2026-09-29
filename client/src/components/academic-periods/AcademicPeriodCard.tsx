import type React from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import type { AcademicPeriod } from '@/lib/api/academic-periods'

interface AcademicPeriodCardProps {
  period: AcademicPeriod
  selected?: boolean
  onSelect: (period: AcademicPeriod) => void
  onEdit: (period: AcademicPeriod) => void
  onConfigureShifts: (period: AcademicPeriod) => void
}

function formatDate(dateStr: string): string {
  const [year, month, day] = dateStr.split('-')
  return `${day}/${month}/${year}`
}

function periodStatus(period: AcademicPeriod): {
  label: string
  variant: 'default' | 'secondary' | 'outline'
} {
  const today = new Date().toISOString().split('T')[0]
  if (today < period.startDate) return { label: 'Futuro', variant: 'outline' }
  if (today > period.endDate) return { label: 'Encerrado', variant: 'secondary' }
  return { label: 'Em andamento', variant: 'default' }
}

export const AcademicPeriodCard: React.FC<AcademicPeriodCardProps> = ({
  period,
  selected,
  onSelect,
  onEdit,
  onConfigureShifts,
}) => {
  const status = periodStatus(period)

  return (
    <Card
      className={`cursor-pointer transition-colors ${selected ? 'ring-2 ring-primary' : 'hover:bg-accent/50'}`}
      onClick={() => onSelect(period)}
    >
      <CardContent className="flex flex-col gap-4 p-5">
        {/* Header: title + badge */}
        <div className="flex items-center justify-between">
          <span className="text-sm font-semibold">{period.name}</span>
          <Badge variant={status.variant}>{status.label}</Badge>
        </div>

        {/* Details row: INÍCIO, FIM, TOTAL */}
        <div className="flex items-start gap-6">
          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] font-medium tracking-wide text-muted-foreground">
              INÍCIO
            </span>
            <span className="text-sm">{formatDate(period.startDate)}</span>
          </div>
          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] font-medium tracking-wide text-muted-foreground">FIM</span>
            <span className="text-sm">{formatDate(period.endDate)}</span>
          </div>
          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] font-medium tracking-wide text-muted-foreground">
              TOTAL
            </span>
            <span className="text-sm">
              {period.holidayCount} feriado{period.holidayCount !== 1 ? 's' : ''}
            </span>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={(e) => {
              e.stopPropagation()
              onEdit(period)
            }}
          >
            Editar
          </Button>
          <Button
            size="sm"
            onClick={(e) => {
              e.stopPropagation()
              onConfigureShifts(period)
            }}
          >
            Configurar Turnos
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
