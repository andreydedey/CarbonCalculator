import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, Check, Monitor, MonitorOff, Pencil, Trash2, X } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { isApiError } from '@/lib/api/client'
import { listConfigurations } from '@/lib/api/configurations'
import {
  type CreateLaboratoryEquipmentPayload,
  getLabComposition,
  type LaboratoryEquipment,
  linkEquipment,
  unlinkEquipment,
  updateLabEquipment,
} from '@/lib/api/laboratory-equipment'
import {
  type LinkEquipmentFormValues,
  linkEquipmentFormSchema,
} from '@/lib/schemas/laboratoryEquipmentSchema'

interface LaboratoryEquipmentSectionProps {
  labId: string
}

function configLabel(config: {
  equipmentModel: { name: string }
  operatingSystem: string
  monitor: { name: string } | null
}): string {
  const parts = [config.equipmentModel.name, config.operatingSystem]
  if (config.monitor) parts.push(config.monitor.name)
  return parts.join(' + ')
}

type EditingState = null | { mode: 'add' } | { mode: 'edit'; item: LaboratoryEquipment }

export const LaboratoryEquipmentSection: React.FC<LaboratoryEquipmentSectionProps> = ({
  labId,
}) => {
  const [editing, setEditing] = useState<EditingState>(null)

  const { data: composition, refetch } = useQuery({
    queryKey: ['lab-composition', labId],
    queryFn: () => getLabComposition(labId),
    enabled: !!labId,
  })

  const { data: configurationsPage } = useQuery({
    queryKey: ['configurations', 'all'],
    queryFn: () => listConfigurations({ size: 100 }),
    enabled: editing?.mode === 'add',
  })

  const items = composition?.items ?? []

  const availableConfigurations = (configurationsPage?.content ?? []).filter(
    (c) => !items.some((i) => i.configurationId === c.id),
  )

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<LinkEquipmentFormValues>({
    resolver: zodResolver(linkEquipmentFormSchema),
    defaultValues: { configurationId: '', quantity: 1 },
  })

  const saveMutation = useMutation({
    mutationFn: (payload: CreateLaboratoryEquipmentPayload) =>
      editing?.mode === 'edit'
        ? updateLabEquipment(labId, editing.item.id, payload)
        : linkEquipment(labId, payload),
    onSuccess: () => {
      const wasEdit = editing?.mode === 'edit'
      closeForm()
      refetch()
      toast.success(
        wasEdit ? 'Quantidade atualizada.' : 'Configuração adicionada ao laboratório.',
      )
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar.')
    },
  })

  const unlinkMutation = useMutation({
    mutationFn: (id: string) => unlinkEquipment(labId, id),
    onSuccess: () => {
      refetch()
      toast.success('Configuração removida do laboratório.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível desvincular.')
    },
  })

  function openAdd() {
    reset({ configurationId: '', quantity: 1 })
    saveMutation.reset()
    setEditing({ mode: 'add' })
  }

  function openEdit(item: LaboratoryEquipment) {
    reset({ configurationId: item.configurationId, quantity: item.quantity })
    saveMutation.reset()
    setEditing({ mode: 'edit', item })
  }

  function closeForm() {
    reset({ configurationId: '', quantity: 1 })
    saveMutation.reset()
    setEditing(null)
  }

  function onSubmit(values: LinkEquipmentFormValues) {
    saveMutation.mutate({
      configurationId: values.configurationId,
      quantity: values.quantity,
    })
  }

  // Prevent Enter inside inline form from submitting the outer lab form
  function blockEnter(e: React.KeyboardEvent) {
    if (e.key === 'Enter') {
      e.preventDefault()
      e.stopPropagation()
      handleSubmit(onSubmit)()
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-semibold">Equipamentos do Laboratório</span>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={openAdd}
          disabled={editing !== null}
        >
          Adicionar Configuração
        </Button>
      </div>

      {editing && (
        <div className="rounded-lg border bg-muted/30 p-3 flex flex-col gap-2">
          <span className="text-xs font-medium text-muted-foreground">
            {editing.mode === 'add' ? 'Nova configuração' : 'Editar quantidade'}
          </span>
          <div className="flex items-start gap-2">
            {editing.mode === 'add' ? (
              <div className="min-w-0 flex-1 flex flex-col gap-1">
                <Select
                  value={watch('configurationId')}
                  onValueChange={(v) =>
                    setValue('configurationId', v, { shouldValidate: true, shouldDirty: true })
                  }
                >
                  <SelectTrigger className="h-8 text-xs w-full">
                    <SelectValue placeholder="Selecione uma configuração" />
                  </SelectTrigger>
                  <SelectContent>
                    {availableConfigurations.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {configLabel(c)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.configurationId && (
                  <span className="text-xs text-destructive">
                    {errors.configurationId.message}
                  </span>
                )}
              </div>
            ) : (
              <div className="flex-1 flex items-center h-8 text-xs text-muted-foreground truncate">
                {configLabel({
                  equipmentModel: editing.item.equipmentModel,
                  operatingSystem: editing.item.operatingSystem,
                  monitor: editing.item.monitor,
                })}
              </div>
            )}
            <div className="w-20 flex flex-col gap-1">
              <Input
                type="number"
                min={1}
                className="h-8 text-xs"
                placeholder="Qtd."
                onKeyDown={blockEnter}
                {...register('quantity')}
              />
              {errors.quantity && (
                <span className="text-xs text-destructive">{errors.quantity.message}</span>
              )}
            </div>
            <Button
              type="button"
              size="icon"
              className="size-8 shrink-0"
              disabled={saveMutation.isPending}
              onClick={handleSubmit(onSubmit)}
            >
              <Check className="size-3.5" />
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="size-8 shrink-0"
              onClick={closeForm}
            >
              <X className="size-3.5" />
            </Button>
          </div>
        </div>
      )}

      {items.length === 0 && !editing ? (
        <>
          <p className="text-xs text-muted-foreground">
            Adicione configurações cadastradas na instituição e informe a quantidade de estações em
            uso neste laboratório.
          </p>
          <div className="rounded-lg border">
            <div className="flex items-center justify-center px-4 py-4">
              <p className="text-xs text-muted-foreground italic">
                Use &ldquo;Adicionar Configuração&rdquo; para vincular configurações da instituição
              </p>
            </div>
          </div>
        </>
      ) : items.length > 0 ? (
        <>
          <div className="rounded-lg border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-2 text-left font-medium">Modelo</th>
                  <th className="px-4 py-2 text-left font-medium">CPU / TDP</th>
                  <th className="px-4 py-2 text-right font-medium">Núcleos</th>
                  <th className="px-4 py-2 text-right font-medium">RAM</th>
                  <th className="px-4 py-2 text-left font-medium">Monitor</th>
                  <th className="px-4 py-2 text-left font-medium">Sistema Op.</th>
                  <th className="px-4 py-2 text-right font-medium">Qtd.</th>
                  <th className="px-4 py-2 text-right font-medium">Ações</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const m = item.equipmentModel
                  const cpuTdp = [m.processor, m.tdpWatts ? `${m.tdpWatts}W` : null]
                    .filter(Boolean)
                    .join(' / ')
                  const hasMonitor = item.monitor != null || m.hasIntegratedScreen
                  const monitorLabel = m.hasIntegratedScreen
                    ? 'Tela integrada'
                    : item.monitor
                      ? [item.monitor.name, item.monitor.watts ? `${item.monitor.watts}W` : null]
                          .filter(Boolean)
                          .join(' / ')
                      : null
                  return (
                    <tr key={item.id} className="border-b last:border-b-0">
                      <td className="px-4 py-2 font-medium">{m.name}</td>
                      <td className="px-4 py-2 font-mono text-xs text-muted-foreground">
                        {cpuTdp || '-'}
                      </td>
                      <td className="px-4 py-2 text-right font-mono text-xs">
                        {m.coreCount ?? '-'}
                      </td>
                      <td className="px-4 py-2 text-right font-mono text-xs">
                        {m.memoryGb ? `${m.memoryGb} GB` : '-'}
                      </td>
                      <td className="px-4 py-2 text-xs">
                        {hasMonitor ? (
                          <span className="flex items-center gap-1 text-muted-foreground">
                            {m.hasIntegratedScreen && <Monitor className="size-3" />}
                            {monitorLabel}
                          </span>
                        ) : (
                          <Badge variant="destructive" className="gap-1">
                            <MonitorOff className="size-3" />
                            Sem monitor
                          </Badge>
                        )}
                      </td>
                      <td className="px-4 py-2">
                        {item.operatingSystem && (
                          <Badge variant="secondary">{item.operatingSystem}</Badge>
                        )}
                      </td>
                      <td className="px-4 py-2 text-right font-mono text-xs">{item.quantity}</td>
                      <td className="px-4 py-2 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="size-7"
                            onClick={() => openEdit(item)}
                            disabled={editing !== null}
                          >
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="size-7 text-destructive"
                            onClick={() => unlinkMutation.mutate(item.id)}
                            disabled={editing !== null}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              Total: <strong className="text-foreground">{composition?.totalMachines}</strong>{' '}
              máquinas
            </span>
            {composition && composition.configurationsWithoutMonitor > 0 && (
              <div className="flex items-center gap-1 text-amber-600">
                <AlertTriangle className="size-3.5" />
                <span>
                  {composition.configurationsWithoutMonitor} configuração(ões) sem monitor — consumo
                  subestimado
                </span>
              </div>
            )}
          </div>
        </>
      ) : null}
    </div>
  )
}
