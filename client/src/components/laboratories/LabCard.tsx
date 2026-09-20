import { Clock3, Cpu, EllipsisVertical, HardDrive, Leaf, Monitor } from 'lucide-react'
import type React from 'react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Progress } from '@/components/ui/progress'
import type { Laboratory } from '@/lib/api/laboratories'

interface SummaryCardProps {
  value: string
  label: string
  icon: React.ReactNode
  variant?: 'accent' | 'default'
}

export const SummaryCard: React.FC<SummaryCardProps> = ({ value, label, icon, variant = 'default' }) => (
  <div
    className={`flex flex-1 items-center gap-3.5 rounded-[10px] border border-border p-5 ${
      variant === 'accent' ? 'bg-accent' : 'bg-card'
    }`}
  >
    <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary">
      {icon}
    </div>
    <div className="flex flex-col gap-0.5">
      <span className="font-mono text-xl font-semibold">{value}</span>
      <span className="text-xs font-medium tracking-[0.6px] text-muted-foreground uppercase">
        {label}
      </span>
    </div>
  </div>
)

const StatusBadge: React.FC<{ active: boolean; label: string }> = ({ active, label }) => (
  <span
    className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-medium ${
      active ? 'bg-accent text-primary' : 'bg-muted text-muted-foreground'
    }`}
  >
    <span
      className={`size-1.5 rounded-full ${active ? 'bg-green-500' : 'bg-muted-foreground'}`}
    />
    {label}
  </span>
)

interface LabCardProps {
  laboratory: Laboratory
  statusLabel: string
  onActivate: (lab: Laboratory) => void
  onDeactivate: (lab: Laboratory) => void
  onDelete: (lab: Laboratory) => void
}

export const LabCard: React.FC<LabCardProps> = ({ laboratory, statusLabel, onActivate, onDeactivate, onDelete }) => (
  <div className="rounded-[10px] border border-border bg-card">
    {/* Top */}
    <div className="flex items-center justify-between px-6 py-5">
      <div className="flex items-center gap-4">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-accent">
          <Cpu className="size-5 text-primary" />
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-base font-semibold">{laboratory.name}</span>
          <span className="text-[13px] text-muted-foreground">-</span>
        </div>
      </div>
      <div className="flex items-center gap-2.5">
        <StatusBadge active={laboratory.active} label={statusLabel} />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline" size="icon" className="size-8">
              <EllipsisVertical className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {laboratory.active ? (
              <DropdownMenuItem onClick={() => onDeactivate(laboratory)}>
                Desativar
              </DropdownMenuItem>
            ) : (
              <DropdownMenuItem onClick={() => onActivate(laboratory)}>
                Ativar
              </DropdownMenuItem>
            )}
            <DropdownMenuItem
              className="text-destructive"
              onClick={() => onDelete(laboratory)}
            >
              Excluir
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>

    {/* Divider */}
    <div className="h-px w-full bg-border" />

    {/* Bottom — stats */}
    <div className="flex items-center gap-8 px-6 py-4">
      <div className="flex items-center gap-2">
        <Monitor className="size-3.5 text-muted-foreground" />
        <span className="text-xs font-medium tracking-[0.6px] text-muted-foreground uppercase">
          Estações:
        </span>
        <span className="text-xs font-semibold">-</span>
      </div>

      <div className="flex items-center gap-2">
        <Leaf className="size-3.5 text-muted-foreground" />
        <span className="text-xs font-medium tracking-[0.6px] text-muted-foreground uppercase">
          Emissão/mês:
        </span>
        <span className="text-xs font-semibold">-</span>
      </div>

      <div className="flex items-center gap-2">
        <Clock3 className="size-3.5 text-muted-foreground" />
        <span className="text-xs font-medium tracking-[0.6px] text-muted-foreground uppercase">
          Turno:
        </span>
        <span className="text-xs font-semibold">-</span>
      </div>

      <div className="flex items-center gap-2">
        <HardDrive className="size-3.5 text-muted-foreground" />
        <span className="text-xs font-medium tracking-[0.6px] text-muted-foreground uppercase">
          Sistema op.:
        </span>
        <span className="text-xs font-semibold">-</span>
      </div>

      <div className="ml-auto flex flex-col items-end gap-1">
        <span className="text-[11px] font-semibold text-primary">- das emissões</span>
        <Progress value={0} className="h-1 w-[100px]" />
      </div>
    </div>
  </div>
)
