import { useMutation, useQuery } from '@tanstack/react-query'
import { Clock3, Cpu, EllipsisVertical, HardDrive, Leaf, Monitor, Plus } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DeactivateDialog } from '@/components/laboratories/DeactivateDialog'
import { DeleteDialog } from '@/components/laboratories/DeleteDialog'
import { LaboratoryForm } from '@/components/laboratories/LaboratoryForm'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Progress } from '@/components/ui/progress'
import { useInstitution } from '@/context/InstitutionContext'
import { getInstitution } from '@/lib/api/institutions'
import { activateLaboratory, listLaboratories, type Laboratory } from '@/lib/api/laboratories'
import { buildListQuery, type LaboratoryViewModel, toViewModel } from '@/lib/laboratories/laboratoryViewModel'

interface SummaryCardProps {
  value: string
  label: string
  icon: React.ReactNode
  variant?: 'accent' | 'default'
}

const SummaryCard: React.FC<SummaryCardProps> = ({ value, label, icon, variant = 'default' }) => (
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
  laboratory: LaboratoryViewModel
  onActivate: (lab: Laboratory) => void
  onDeactivate: (lab: Laboratory) => void
  onDelete: (lab: Laboratory) => void
}

const LabCard: React.FC<LabCardProps> = ({ laboratory, onActivate, onDeactivate, onDelete }) => {
  const labData: Laboratory = {
    id: laboratory.id,
    name: laboratory.name,
    active: laboratory.active,
  }

  return (
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
          <StatusBadge active={laboratory.active} label={laboratory.statusLabel} />
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" className="size-8">
                <EllipsisVertical className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {laboratory.active ? (
                <DropdownMenuItem onClick={() => onDeactivate(labData)}>
                  Desativar
                </DropdownMenuItem>
              ) : (
                <DropdownMenuItem onClick={() => onActivate(labData)}>
                  Ativar
                </DropdownMenuItem>
              )}
              <DropdownMenuItem
                className="text-destructive"
                onClick={() => onDelete(labData)}
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
}

export const LaboratoryList: React.FC = () => {
  const { institutionId } = useInstitution()
  const [showInactive, setShowInactive] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [deactivateTarget, setDeactivateTarget] = useState<Laboratory | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Laboratory | null>(null)

  const { data: institution } = useQuery({
    queryKey: ['institution', institutionId],
    queryFn: () => getInstitution(institutionId!),
    enabled: !!institutionId,
  })

  const { data: laboratories = [], isLoading, refetch } = useQuery({
    queryKey: ['laboratories', showInactive],
    queryFn: () => listLaboratories(buildListQuery(showInactive)),
    select: (data) => data.map(toViewModel),
  })

  const activateMutation = useMutation({
    mutationFn: (lab: Laboratory) => activateLaboratory(lab.id),
    onSuccess: () => {
      refetch()
      toast.success('Laboratório ativado.')
    },
  })

  function handleSaved() {
    setShowForm(false)
    refetch()
  }

  function handleDeactivated() {
    setDeactivateTarget(null)
    refetch()
  }

  function handleDeleted() {
    setDeleteTarget(null)
    refetch()
  }

  const institutionName = institution?.acronym ?? institution?.name ?? ''
  const totalLabs = laboratories.length

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-normal text-muted-foreground">Cadastro &rsaquo; Laboratórios</p>
          <h1 className="font-heading text-2xl font-bold">
            Laboratórios{institutionName ? ` — ${institutionName}` : ''}
          </h1>
        </div>
        <Button onClick={() => setShowForm(true)}>
          <Plus className="size-4" />
          Novo Laboratório
        </Button>
      </div>

      <LaboratoryForm open={showForm} onOpenChange={setShowForm} onSaved={handleSaved} />

      <div className="flex gap-4">
        <SummaryCard
          value={String(totalLabs)}
          label="Total de Laboratórios"
          icon={<Cpu className="size-[18px] text-primary-foreground" />}
          variant="accent"
        />
        <SummaryCard
          value="-"
          label="Estações Ativas"
          icon={<Monitor className="size-[18px] text-primary-foreground" />}
        />
        <SummaryCard
          value="-"
          label="Emissão Mensal Total"
          icon={<Leaf className="size-[18px] text-primary-foreground" />}
        />
      </div>

      <div className="flex items-center">
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            checked={showInactive}
            onChange={(event) => setShowInactive(event.target.checked)}
          />
          Mostrar inativos
        </label>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : laboratories.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum laboratório encontrado.</p>
      ) : (
        <div className="flex flex-col gap-4">
          {laboratories.map((laboratory) => (
            <LabCard
              key={laboratory.id}
              laboratory={laboratory}
              onActivate={(lab) => activateMutation.mutate(lab)}
              onDeactivate={setDeactivateTarget}
              onDelete={setDeleteTarget}
            />
          ))}
        </div>
      )}

      {deactivateTarget && (
        <DeactivateDialog
          laboratory={deactivateTarget}
          open={!!deactivateTarget}
          onOpenChange={(open) => !open && setDeactivateTarget(null)}
          onDeactivated={handleDeactivated}
        />
      )}

      {deleteTarget && (
        <DeleteDialog
          laboratory={deleteTarget}
          open={!!deleteTarget}
          onOpenChange={(open) => !open && setDeleteTarget(null)}
          onDeleted={handleDeleted}
        />
      )}
    </div>
  )
}
