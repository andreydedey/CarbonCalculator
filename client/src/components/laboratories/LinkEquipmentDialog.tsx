import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
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
import {
  type LaboratoryEquipmentFormValues,
  laboratoryEquipmentFormSchema,
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
      quantity: equipment?.quantity ?? 1,
    },
  })

  const selectedModelId = watch('equipmentModelId')
  const selectedModel = models.find((m) => m.id === selectedModelId)

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
    mutation.mutate({
      equipmentModelId: values.equipmentModelId,
      operatingSystem: values.operatingSystem.trim(),
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
            Selecione um modelo de equipamento e informe o sistema operacional e a quantidade.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="flex flex-col gap-1.5">
              <Label>Modelo de Equipamento *</Label>
              <Select
                value={selectedModelId}
                onValueChange={(v) => setValue('equipmentModelId', v)}
                disabled={mode === 'edit'}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione um modelo" />
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

            {selectedModel && !selectedModel.hasMonitor && (
              <div className="flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
                <AlertTriangle className="size-3.5 shrink-0" />
                <span>
                  Este modelo não possui dados de monitor. O consumo calculado ficará subestimado.
                </span>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="link-os">Sistema Operacional *</Label>
              <Input
                id="link-os"
                placeholder="Ex: Windows 11"
                aria-invalid={!!errors.operatingSystem}
                {...register('operatingSystem')}
              />
              <FieldError message={errors.operatingSystem?.message} />
            </div>

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
