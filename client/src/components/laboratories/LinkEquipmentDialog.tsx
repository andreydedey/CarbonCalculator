import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import type React from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
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
import { listConfigurations } from '@/lib/api/configurations'
import {
  type CreateLaboratoryEquipmentPayload,
  type LaboratoryEquipment,
  linkEquipment,
  updateLabEquipment,
} from '@/lib/api/laboratory-equipment'

const linkFormSchema = z.object({
  configurationId: z.string().min(1, 'Selecione uma configuração'),
  quantity: z.coerce.number().int().min(1, 'A quantidade deve ser pelo menos 1'),
})

type LinkFormValues = z.infer<typeof linkFormSchema>

interface LinkEquipmentDialogProps {
  labId: string
  equipment?: LaboratoryEquipment
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
}

function configLabel(config: { equipmentModel: { name: string }; operatingSystem: string; monitor: { name: string } | null }): string {
  const parts = [config.equipmentModel.name, config.operatingSystem]
  if (config.monitor) parts.push(config.monitor.name)
  return parts.join(' + ')
}

export const LinkEquipmentDialog: React.FC<LinkEquipmentDialogProps> = ({
  labId,
  equipment,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = equipment ? 'edit' : 'create'

  const { data: configurationsPage } = useQuery({
    queryKey: ['configurations', 'all'],
    queryFn: () => listConfigurations({ size: 100 }),
    enabled: open,
  })
  const configurations = configurationsPage?.content ?? []

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<LinkFormValues>({
    resolver: zodResolver(linkFormSchema),
    values: {
      configurationId: equipment?.configurationId ?? '',
      quantity: equipment?.quantity ?? 1,
    },
  })

  const saveMutation = useMutation({
    mutationFn: (payload: CreateLaboratoryEquipmentPayload) =>
      mode === 'edit' && equipment
        ? updateLabEquipment(labId, equipment.id, payload)
        : linkEquipment(labId, payload),
    onSuccess: () => {
      reset()
      onOpenChange(false)
      onSaved?.()
      toast.success(mode === 'edit' ? 'Quantidade atualizada.' : 'Configuração adicionada ao laboratório.')
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar.')
    },
  })

  function onSubmit(values: LinkFormValues) {
    saveMutation.mutate({
      configurationId: values.configurationId,
      quantity: values.quantity,
    })
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
      <DialogContent className="sm:max-w-[500px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Quantidade' : 'Adicionar Configuração'}
          </DialogTitle>
          <DialogDescription>
            {mode === 'edit'
              ? 'Altere a quantidade de estações com esta configuração.'
              : 'Selecione uma configuração cadastrada e informe a quantidade de estações.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="flex flex-col gap-1.5">
              <Label>Configuração *</Label>
              <Select
                value={watch('configurationId')}
                onValueChange={(v) => setValue('configurationId', v)}
                disabled={mode === 'edit'}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione uma configuração" />
                </SelectTrigger>
                <SelectContent>
                  {configurations.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {configLabel(c)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError message={errors.configurationId?.message} />
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
            <Button type="submit" disabled={saveMutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Adicionar'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
