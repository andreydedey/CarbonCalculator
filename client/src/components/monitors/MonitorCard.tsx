import { EllipsisVertical, Monitor as MonitorIcon } from 'lucide-react'
import type React from 'react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { Monitor } from '@/lib/api/monitors'

interface MonitorCardProps {
  monitor: Monitor
  onEdit: (monitor: Monitor) => void
  onDelete: (monitor: Monitor) => void
}

export const MonitorCard: React.FC<MonitorCardProps> = ({ monitor, onEdit, onDelete }) => (
  <div
    role="button"
    tabIndex={0}
    className="w-full rounded-[10px] border border-border bg-card cursor-pointer transition-colors hover:bg-muted/40 text-left"
    onClick={() => onEdit(monitor)}
    onKeyDown={(e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        onEdit(monitor)
      }
    }}
  >
    <div className="flex items-center justify-between px-6 py-5">
      <div className="flex items-center gap-4">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-accent">
          <MonitorIcon className="size-5 text-primary" />
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-base font-semibold">{monitor.name}</span>
          <span className="text-[13px] text-muted-foreground">
            {monitor.watts ? `${monitor.watts}W` : '-'}
          </span>
        </div>
      </div>
      <div className="flex items-center gap-2.5">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="outline"
              size="icon"
              className="size-8"
              onClick={(e) => e.stopPropagation()}
            >
              <EllipsisVertical className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => onEdit(monitor)}>Editar</DropdownMenuItem>
            <DropdownMenuItem className="text-destructive" onClick={() => onDelete(monitor)}>
              Excluir
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  </div>
)
