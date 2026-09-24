import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import type React from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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
import { isApiError } from '@/lib/api/client'
import {
  type CreateEquipmentModelPayload,
  createEquipmentModel,
  type EquipmentModel,
  updateEquipmentModel,
} from '@/lib/api/equipment-models'
import {
  EQUIPMENT_TYPES,
  type EquipmentModelFormValues,
  equipmentModelFormSchema,
  MEMORY_OPTIONS,
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
    setValue,
    formState: { errors },
  } = useForm<EquipmentModelFormValues>({
    resolver: zodResolver(equipmentModelFormSchema),
    values: {
      name: model?.name ?? '',
      equipmentType: model?.equipmentType ?? '',
      processor: model?.processor ?? '',
      tdpWatts: model?.tdpWatts ?? undefined,
      coreCount: model?.coreCount ?? undefined,
      memoryGb: model?.memoryGb ?? undefined,
      gpuModel: model?.gpuModel ?? '',
      gpuTdpWatts: model?.gpuTdpWatts ?? undefined,
      hasIntegratedScreen: model?.hasIntegratedScreen ?? false,
      description: model?.description ?? '',
    },
  })

  const gpuModel = watch('gpuModel')

  const saveMutation = useMutation({
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
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar o modelo.')
    },
  })

  function onSubmit(values: EquipmentModelFormValues) {
    const payload: CreateEquipmentModelPayload = {
      name: values.name.trim(),
      ...(values.equipmentType ? { equipmentType: values.equipmentType } : {}),
      ...(values.processor?.trim() ? { processor: values.processor.trim() } : {}),
      ...(values.tdpWatts ? { tdpWatts: values.tdpWatts } : {}),
      ...(values.coreCount ? { coreCount: values.coreCount } : {}),
      ...(values.memoryGb ? { memoryGb: values.memoryGb } : {}),
      ...(values.gpuModel?.trim() ? { gpuModel: values.gpuModel.trim() } : {}),
      ...(values.gpuTdpWatts ? { gpuTdpWatts: values.gpuTdpWatts } : {}),
      hasIntegratedScreen: values.hasIntegratedScreen,
      ...(values.description?.trim() ? { description: values.description.trim() } : {}),
    }
    saveMutation.mutate(payload)
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      reset()
      saveMutation.reset()
    }
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[700px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Computador' : 'Novo Computador'}
          </DialogTitle>
          <DialogDescription>Cadastre um modelo de computador na instituição</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            {/* Row 1: Modelo + Tipo */}
            <div className="grid grid-cols-[1fr_auto] gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-name">Modelo do Computador *</Label>
                <Input
                  id="model-name"
                  placeholder="Ex: Dell OptiPlex 7090"
                  aria-invalid={!!errors.name}
                  {...register('name')}
                />
                <FieldError message={errors.name?.message} />
              </div>
              <div className="flex flex-col gap-1.5 min-w-[180px]">
                <Label>Tipo</Label>
                <Select
                  value={watch('equipmentType') || ''}
                  onValueChange={(v) => setValue('equipmentType', v)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecionar..." />
                  </SelectTrigger>
                  <SelectContent>
                    {EQUIPMENT_TYPES.map((t) => (
                      <SelectItem key={t} value={t}>
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {/* Row 2: CPU + TDP + Núcleos */}
            <div className="grid grid-cols-3 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-processor">Processador (CPU)</Label>
                <Input
                  id="model-processor"
                  placeholder="Ex: Intel i7-10700"
                  {...register('processor')}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-tdp">TDP (Watts)</Label>
                <Input
                  id="model-tdp"
                  type="number"
                  placeholder="Ex: 65"
                  {...register('tdpWatts')}
                />
                <FieldError message={errors.tdpWatts?.message} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-cores">Núcleos</Label>
                <Input
                  id="model-cores"
                  type="number"
                  placeholder="Ex: 8"
                  {...register('coreCount')}
                />
                <FieldError message={errors.coreCount?.message} />
              </div>
            </div>

            {/* Row 3: RAM + GPU Modelo + GPU TDP */}
            <div className="grid grid-cols-3 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label>Memória RAM</Label>
                <Select
                  value={watch('memoryGb')?.toString() || ''}
                  onValueChange={(v) => setValue('memoryGb', Number(v))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecionar..." />
                  </SelectTrigger>
                  <SelectContent>
                    {MEMORY_OPTIONS.map((gb) => (
                      <SelectItem key={gb} value={String(gb)}>
                        {gb} GB
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="gpu-model">GPU Dedicada (Modelo)</Label>
                <Input id="gpu-model" placeholder="Ex: NVIDIA GTX 1650" {...register('gpuModel')} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="gpu-tdp">GPU TDP (Watts)</Label>
                <Input
                  id="gpu-tdp"
                  type="number"
                  placeholder="Ex: 75"
                  disabled={!gpuModel}
                  {...register('gpuTdpWatts')}
                />
                <FieldError message={errors.gpuTdpWatts?.message} />
              </div>
            </div>

            {/* Row 4: Tela integrada + Descrição */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center gap-2 pt-6">
                <Checkbox
                  id="has-integrated-screen"
                  checked={watch('hasIntegratedScreen')}
                  onCheckedChange={(checked) => setValue('hasIntegratedScreen', checked === true)}
                />
                <Label htmlFor="has-integrated-screen" className="cursor-pointer">
                  Tela integrada (notebook / all-in-one)
                </Label>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="model-description">Descrição (opcional)</Label>
                <Input
                  id="model-description"
                  placeholder="Observações sobre o equipamento"
                  {...register('description')}
                />
              </div>
            </div>
          </div>

          <DialogFooter className="mx-0 mb-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Cadastrar Computador'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
