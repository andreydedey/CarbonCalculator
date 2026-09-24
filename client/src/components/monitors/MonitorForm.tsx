import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
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
  type CreateMonitorPayload,
  createMonitor,
  type Monitor,
  updateMonitor,
} from '@/lib/api/monitors'
import { type MonitorFormValues, monitorFormSchema } from '@/lib/schemas/monitorSchema'

interface MonitorFormProps {
  monitor?: Monitor
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
}

export const MonitorForm: React.FC<MonitorFormProps> = ({
  monitor,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = monitor ? 'edit' : 'create'
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<MonitorFormValues>({
    resolver: zodResolver(monitorFormSchema),
    values: {
      name: monitor?.name ?? '',
      watts: monitor?.watts ?? undefined,
    },
  })

  const mutation = useMutation({
    mutationFn: (payload: CreateMonitorPayload) =>
      mode === 'edit' && monitor ? updateMonitor(monitor.id, payload) : createMonitor(payload),
    onSuccess: () => {
      reset()
      onOpenChange(false)
      onSaved?.()
      toast.success(mode === 'edit' ? 'Monitor atualizado.' : 'Monitor criado.')
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível salvar o monitor.')
    },
  })

  function onSubmit(values: MonitorFormValues) {
    const payload: CreateMonitorPayload = {
      name: values.name.trim(),
      ...(values.watts ? { watts: values.watts } : {}),
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
      <DialogContent className="sm:max-w-[500px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Monitor' : 'Novo Monitor'}
          </DialogTitle>
          <DialogDescription>Cadastre um monitor na instituição</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="monitor-name">Nome do Monitor *</Label>
                <Input
                  id="monitor-name"
                  placeholder="Ex: Dell P2422H"
                  aria-invalid={!!errors.name}
                  {...register('name')}
                />
                <FieldError message={errors.name?.message} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="monitor-watts">Potência nominal (Watts)</Label>
                <Input
                  id="monitor-watts"
                  type="number"
                  placeholder="Ex: 25"
                  {...register('watts')}
                />
                <FieldError message={errors.watts?.message} />
              </div>
            </div>
          </div>

          <DialogFooter className="mx-0 mb-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Cadastrar Monitor'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
