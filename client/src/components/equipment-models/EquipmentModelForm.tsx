import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
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
import { ApiError } from '@/lib/api/client'
import {
  type CreateEquipmentModelPayload,
  createEquipmentModel,
  type EquipmentModel,
  updateEquipmentModel,
} from '@/lib/api/equipment-models'
import {
  type EquipmentModelFormValues,
  equipmentModelFormSchema,
} from '@/lib/schemas/equipmentModelSchema'

interface EquipmentModelFormProps {
  model?: EquipmentModel
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
}

export const EquipmentModelForm: React.FC<EquipmentModelFormProps> = ({
  model,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = model ? 'edit' : 'create'
  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<EquipmentModelFormValues>({
    resolver: zodResolver(equipmentModelFormSchema),
    values: {
      name: model?.name ?? '',
      processor: model?.processor ?? '',
      memoryGb: model?.memoryGb ?? undefined,
      hasDedicatedGpu: model?.hasDedicatedGpu ?? false,
      gpuModel: model?.gpuModel ?? '',
      monitorName: model?.monitorName ?? '',
      monitorSizeInches: model?.monitorSizeInches ?? undefined,
      monitorResolution: model?.monitorResolution ?? '',
    },
  })

  const monitorName = watch('monitorName')
  const monitorSizeInches = watch('monitorSizeInches')
  const monitorResolution = watch('monitorResolution')
  const noMonitor = !monitorName && !monitorSizeInches && !monitorResolution

  const mutation = useMutation({
    mutationFn: (payload: CreateEquipmentModelPayload) =>
      mode === 'edit' && model
        ? updateEquipmentModel(model.id, payload)
        : createEquipmentModel(payload),
    onSuccess: () => {
      reset()
      onOpenChange(false)
      onSaved?.()
      toast.success(mode === 'edit' ? 'Modelo atualizado.' : 'Modelo criado.')
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar o modelo.')
    },
  })

  function onSubmit(values: EquipmentModelFormValues) {
    const payload: CreateEquipmentModelPayload = {
      name: values.name.trim(),
      ...(values.processor?.trim() ? { processor: values.processor.trim() } : {}),
      ...(values.memoryGb ? { memoryGb: values.memoryGb } : {}),
      hasDedicatedGpu: values.hasDedicatedGpu,
      ...(values.gpuModel?.trim() ? { gpuModel: values.gpuModel.trim() } : {}),
      ...(values.monitorName?.trim() ? { monitorName: values.monitorName.trim() } : {}),
      ...(values.monitorSizeInches ? { monitorSizeInches: values.monitorSizeInches } : {}),
      ...(values.monitorResolution?.trim()
        ? { monitorResolution: values.monitorResolution.trim() }
        : {}),
    }
    mutation.mutate(payload)
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
      <DialogContent className="sm:max-w-[600px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Modelo' : 'Novo Modelo de Equipamento'}
          </DialogTitle>
          <DialogDescription>
            Informe as especificações de hardware e monitor do modelo.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6 max-h-[60vh] overflow-y-auto">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="model-name">Nome do Modelo *</Label>
              <Input
                id="model-name"
                placeholder="Ex: Dell OptiPlex 3070"
                aria-invalid={!!errors.name}
                {...register('name')}
              />
              <FieldError message={errors.name?.message} />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-processor">Processador</Label>
                <Input
                  id="model-processor"
                  placeholder="Ex: Intel Core i5-9500"
                  {...register('processor')}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-memory">Memória (GB)</Label>
                <Input
                  id="model-memory"
                  type="number"
                  placeholder="Ex: 8"
                  {...register('memoryGb')}
                />
                <FieldError message={errors.memoryGb?.message} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center gap-2">
                <input
                  id="model-gpu"
                  type="checkbox"
                  className="size-4 rounded border-border"
                  {...register('hasDedicatedGpu')}
                />
                <Label htmlFor="model-gpu">GPU dedicada</Label>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-gpu-model">Modelo da GPU</Label>
                <Input
                  id="model-gpu-model"
                  placeholder="Ex: NVIDIA GTX 1650"
                  {...register('gpuModel')}
                />
              </div>
            </div>

            <div className="h-px bg-border" />

            <span className="text-sm font-semibold">Monitor</span>

            <div className="grid grid-cols-3 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="monitor-name">Nome</Label>
                <Input
                  id="monitor-name"
                  placeholder="Ex: Dell P2419H"
                  {...register('monitorName')}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="monitor-size">Tamanho (pol.)</Label>
                <Input
                  id="monitor-size"
                  type="number"
                  step="0.1"
                  placeholder="Ex: 23.8"
                  {...register('monitorSizeInches')}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="monitor-resolution">Resolução</Label>
                <Input
                  id="monitor-resolution"
                  placeholder="Ex: 1920x1080"
                  {...register('monitorResolution')}
                />
              </div>
            </div>

            {noMonitor && (
              <div className="flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
                <AlertTriangle className="size-3.5 shrink-0" />
                <span>Sem dados de monitor, o consumo calculado ficará subestimado.</span>
              </div>
            )}
          </div>

          <DialogFooter className="mx-0 mb-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Criar Modelo'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
