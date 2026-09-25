import { Calendar, Copy, MoreHorizontal, Pencil, Trash2 } from 'lucide-react'
import type React from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { AcademicPeriod } from '@/lib/api/academic-periods'

interface AcademicPeriodCardProps {
  period: AcademicPeriod
  onEdit: (period: AcademicPeriod) => void
  onDelete: (period: AcademicPeriod) => void
  onCopy: (period: AcademicPeriod) => void
}

function formatDate(dateStr: string): string {
  const [year, month, day] = dateStr.split('-')
  return `${day}/${month}/${year}`
}

function periodStatus(period: AcademicPeriod): { label: string; variant: 'default' | 'secondary' | 'outline' } {
  const today = new Date().toISOString().split('T')[0]
  if (today < period.startDate) return { label: 'Futuro', variant: 'outline' }
  if (today > period.endDate) return { label: 'Encerrado', variant: 'secondary' }
  return { label: 'Em andamento', variant: 'default' }
}

export const AcademicPeriodCard: React.FC<AcademicPeriodCardProps> = ({
  period,
  onEdit,
  onDelete,
  onCopy,
}) => {
  const navigate = useNavigate()
  const status = periodStatus(period)

  return (
    <Card
      className="cursor-pointer hover:bg-accent/50 transition-colors"
      onClick={() => navigate(`/academic-periods/${period.id}`)}
    >
      <CardContent className="flex items-center justify-between py-4 px-5">
        <div className="flex items-center gap-4">
          <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10">
            <Calendar className="size-5 text-primary" />
          </div>
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-semibold">{period.name}</span>
            <span className="text-xs text-muted-foreground">
              {formatDate(period.startDate)} — {formatDate(period.endDate)}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Badge variant={status.variant}>{status.label}</Badge>
          <span className="text-xs text-muted-foreground">
            {period.holidayCount} feriado{period.holidayCount !== 1 ? 's' : ''}
          </span>
          <DropdownMenu>
            <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
              <Button variant="ghost" size="icon" className="size-8">
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                onClick={(e) => {
                  e.stopPropagation()
                  onEdit(period)
                }}
              >
                <Pencil className="mr-2 size-4" />
                Editar
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={(e) => {
                  e.stopPropagation()
                  onCopy(period)
                }}
              >
                <Copy className="mr-2 size-4" />
                Copiar
              </DropdownMenuItem>
              <DropdownMenuItem
                className="text-destructive"
                onClick={(e) => {
                  e.stopPropagation()
                  onDelete(period)
                }}
              >
                <Trash2 className="mr-2 size-4" />
                Excluir
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </CardContent>
    </Card>
  )
}
