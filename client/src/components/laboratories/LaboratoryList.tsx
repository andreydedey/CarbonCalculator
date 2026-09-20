import { useMutation, useQuery } from '@tanstack/react-query'
import { Cpu, Leaf, Monitor, Plus } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { toast } from 'sonner'
import { DeactivateDialog } from '@/components/laboratories/DeactivateDialog'
import { DeleteDialog } from '@/components/laboratories/DeleteDialog'
import { LabCard, SummaryCard } from '@/components/laboratories/LabCard'
import { LaboratoryForm } from '@/components/laboratories/LaboratoryForm'
import { Button } from '@/components/ui/button'
import { useInstitution } from '@/context/InstitutionContext'
import { getInstitution } from '@/lib/api/institutions'
import { activateLaboratory, type Laboratory, listLaboratories } from '@/lib/api/laboratories'

function statusLabel(lab: Laboratory): string {
  return lab.active ? 'Operando' : 'Inativo'
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

  const {
    data: laboratories = [],
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ['laboratories', showInactive],
    queryFn: () => listLaboratories(showInactive ? { includeInactive: true } : {}),
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
          <p className="text-xs font-normal text-muted-foreground">
            Cadastro &rsaquo; Laboratórios
          </p>
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
              statusLabel={statusLabel(laboratory)}
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
