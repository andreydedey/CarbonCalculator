import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, Monitor } from 'lucide-react'
import type React from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ApiError } from '@/lib/api/client'
import { listEquipmentModels } from '@/lib/api/equipment-models'
import {
  type CreateLaboratoryEquipmentPayload,
  type LaboratoryEquipment,
  linkEquipment,
  updateLabEquipment,
} from '@/lib/api/laboratory-equipment'
import { listMonitors } from '@/lib/api/monitors'
import {
  type LaboratoryEquipmentFormValues,
  laboratoryEquipmentFormSchema,
  OS_OPTIONS,
} from '@/lib/schemas/laboratoryEquipmentSchema'

interface LinkEquipmentDialogProps {
  labId: string
  equipment?: LaboratoryEquipment
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
}

export const LinkEquipmentDialog: React.FC<LinkEquipmentDialogProps> = ({
  labId,
  equipment,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = equipment ? 'edit' : 'create'

  const { data: modelsPage } = useQuery({
    queryKey: ['equipment-models', 'all'],
    queryFn: () => listEquipmentModels({ size: 100 }),
    enabled: open,
  })
  const models = modelsPage?.content ?? []

  const { data: monitorsPage } = useQuery({
    queryKey: ['monitors', 'all'],
    queryFn: () => listMonitors({ size: 100 }),
    enabled: open,
  })
  const monitors = monitorsPage?.content ?? []

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<LaboratoryEquipmentFormValues>({
    resolver: zodResolver(laboratoryEquipmentFormSchema),
    values: {
      equipmentModelId: equipment?.equipmentModel.id ?? '',
      operatingSystem: equipment?.operatingSystem ?? '',
      monitorId: equipment?.monitor?.id ?? '',
      quantity: equipment?.quantity ?? 1,
    },
  })

  const selectedModelId = watch('equipmentModelId')
  const selectedModel = models.find((m) => m.id === selectedModelId)
  const hasIntegratedScreen = selectedModel?.hasIntegratedScreen ?? false

  const mutation = useMutation({
    mutationFn: (payload: CreateLaboratoryEquipmentPayload) =>
      mode === 'edit' && equipment
        ? updateLabEquipment(labId, equipment.id, payload)
        : linkEquipment(labId, payload),
    onSuccess: () => {
      reset()
      onOpenChange(false)
      onSaved?.()
      toast.success(mode === 'edit' ? 'Configuração atualizada.' : 'Equipamento vinculado.')
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar o vínculo.')
    },
  })

  function onSubmit(values: LaboratoryEquipmentFormValues) {
    const monitorId = hasIntegratedScreen ? undefined : values.monitorId || undefined
    mutation.mutate({
      equipmentModelId: values.equipmentModelId,
      operatingSystem: values.operatingSystem.trim(),
      monitorId: monitorId ?? null,
      quantity: values.quantity,
    })
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      reset()
      mutation.reset()
    }
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[500px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Configuração' : 'Vincular Equipamento'}
          </DialogTitle>
          <DialogDescription>
            Selecione um computador, sistema operacional, monitor e a quantidade.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="flex flex-col gap-1.5">
              <Label>Computador *</Label>
              <Select
                value={selectedModelId}
                onValueChange={(v) => setValue('equipmentModelId', v)}
                disabled={mode === 'edit'}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione um computador" />
                </SelectTrigger>
                <SelectContent>
                  {models.map((m) => (
                    <SelectItem key={m.id} value={m.id}>
                      {m.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError message={errors.equipmentModelId?.message} />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label>Sistema Operacional *</Label>
              <Select
                value={watch('operatingSystem')}
                onValueChange={(v) => setValue('operatingSystem', v)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione o sistema operacional" />
                </SelectTrigger>
                <SelectContent>
                  {OS_OPTIONS.map((os) => (
                    <SelectItem key={os} value={os}>
                      {os}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError message={errors.operatingSystem?.message} />
            </div>

            {hasIntegratedScreen ? (
              <div className="flex items-center gap-2 rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-800 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200">
                <Monitor className="size-3.5 shrink-0" />
                <span>Este computador possui tela integrada — monitor externo não aplicável.</span>
              </div>
            ) : (
              <div className="flex flex-col gap-1.5">
                <Label>Monitor</Label>
                <Select
                  value={watch('monitorId') || ''}
                  onValueChange={(v) => setValue('monitorId', v === '__none__' ? '' : v)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Nenhum (sem monitor externo)" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">Nenhum</SelectItem>
                    {monitors.map((mon) => (
                      <SelectItem key={mon.id} value={mon.id}>
                        {mon.name}
                        {mon.watts ? ` (${mon.watts}W)` : ''}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldError message={errors.monitorId?.message} />
              </div>
            )}

            {!hasIntegratedScreen && !watch('monitorId') && selectedModel && (
              <div className="flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
                <AlertTriangle className="size-3.5 shrink-0" />
                <span>Nenhum monitor selecionado. O consumo calculado ficará subestimado.</span>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="link-qty">Quantidade *</Label>
              <Input
                id="link-qty"
                type="number"
                min={1}
                aria-invalid={!!errors.quantity}
                {...register('quantity')}
              />
              <FieldError message={errors.quantity?.message} />
            </div>
          </div>

          <DialogFooter className="mx-0 mb-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Vincular'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
