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
import { isApiError } from '@/lib/api/client'
import {
  type AcademicPeriod,
  type CreateAcademicPeriodPayload,
  createAcademicPeriod,
  updateAcademicPeriod,
} from '@/lib/api/academic-periods'
import {
  type AcademicPeriodFormValues,
  academicPeriodFormSchema,
} from '@/lib/schemas/academicPeriodSchema'

interface AcademicPeriodFormProps {
  period?: AcademicPeriod
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
}

export const AcademicPeriodForm: React.FC<AcademicPeriodFormProps> = ({
  period,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = period ? 'edit' : 'create'
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<AcademicPeriodFormValues>({
    resolver: zodResolver(academicPeriodFormSchema),
    values: {
      name: period?.name ?? '',
      startDate: period?.startDate ?? '',
      endDate: period?.endDate ?? '',
    },
  })

  const mutation = useMutation({
    mutationFn: (payload: CreateAcademicPeriodPayload) =>
      mode === 'edit' && period
        ? updateAcademicPeriod(period.id, payload)
        : createAcademicPeriod(payload),
    onSuccess: () => {
      reset()
      onOpenChange(false)
      onSaved?.()
      toast.success(mode === 'edit' ? 'Período atualizado.' : 'Período criado.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar o período.')
    },
  })

  function onSubmit(values: AcademicPeriodFormValues) {
    mutation.mutate({
      name: values.name.trim(),
      startDate: values.startDate,
      endDate: values.endDate,
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
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Período Letivo' : 'Novo Período Letivo'}
          </DialogTitle>
          <DialogDescription>
            Informe o nome e as datas de início e fim do período.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="period-name">Nome do Período *</Label>
            <Input
              id="period-name"
              placeholder="Ex: 2024.1"
              aria-invalid={!!errors.name}
              {...register('name')}
            />
            <FieldError message={errors.name?.message} />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="period-start">Data de Início *</Label>
              <Input
                id="period-start"
                type="date"
                aria-invalid={!!errors.startDate}
                {...register('startDate')}
              />
              <FieldError message={errors.startDate?.message} />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="period-end">Data de Fim *</Label>
              <Input
                id="period-end"
                type="date"
                aria-invalid={!!errors.endDate}
                {...register('endDate')}
              />
              <FieldError message={errors.endDate?.message} />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={!isDirty || mutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Criar Período'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
