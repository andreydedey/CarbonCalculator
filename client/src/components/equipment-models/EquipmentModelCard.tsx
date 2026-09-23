import { Cpu, EllipsisVertical, Monitor } from 'lucide-react'
import type React from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { EquipmentModel } from '@/lib/api/equipment-models'

function cpuLabel(model: EquipmentModel): string {
  const parts = [model.processor, model.tdpWatts ? `${model.tdpWatts}W` : null].filter(Boolean)
  return parts.join(' / ') || '-'
}

interface EquipmentModelCardProps {
  model: EquipmentModel
  onEdit: (model: EquipmentModel) => void
  onDelete: (model: EquipmentModel) => void
}

export const EquipmentModelCard: React.FC<EquipmentModelCardProps> = ({
  model,
  onEdit,
  onDelete,
}) => (
  <button
    type="button"
    className="w-full rounded-[10px] border border-border bg-card cursor-pointer transition-colors hover:bg-muted/40 text-left"
    onClick={() => onEdit(model)}
  >
    <div className="flex items-center justify-between px-6 py-5">
      <div className="flex items-center gap-4">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-accent">
          <Cpu className="size-5 text-primary" />
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-base font-semibold">{model.name}</span>
          <span className="text-[13px] text-muted-foreground">{cpuLabel(model)}</span>
        </div>
      </div>
      <div className="flex items-center gap-2.5">
        {model.equipmentType && (
          <Badge variant="secondary" className="gap-1">
            {model.equipmentType}
          </Badge>
        )}
        {model.hasIntegratedScreen && (
          <Badge variant="outline" className="gap-1">
            <Monitor className="size-3" />
            Tela integrada
          </Badge>
        )}
        {model.gpuModel && (
          <Badge variant="secondary" className="gap-1">
            GPU: {model.gpuModel}
          </Badge>
        )}
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
            <DropdownMenuItem onClick={() => onEdit(model)}>Editar</DropdownMenuItem>
            <DropdownMenuItem className="text-destructive" onClick={() => onDelete(model)}>
              Excluir
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>

    <div className="h-px w-full bg-border" />

    <div className="flex items-center gap-8 px-6 py-4 text-xs">
      <div className="flex items-center gap-1.5">
        <span className="font-medium tracking-[0.6px] text-muted-foreground uppercase">
          Núcleos:
        </span>
        <span className="font-semibold">{model.coreCount ?? '-'}</span>
      </div>
      <div className="flex items-center gap-1.5">
        <span className="font-medium tracking-[0.6px] text-muted-foreground uppercase">RAM:</span>
        <span className="font-semibold">{model.memoryGb ? `${model.memoryGb} GB` : '-'}</span>
      </div>
      {model.gpuModel && (
        <div className="flex items-center gap-1.5">
          <span className="font-medium tracking-[0.6px] text-muted-foreground uppercase">
            GPU TDP:
          </span>
          <span className="font-semibold">{model.gpuTdpWatts ? `${model.gpuTdpWatts}W` : '-'}</span>
        </div>
      )}
    </div>
  </button>
)
