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
import { type AcademicPeriod, copyPeriod } from '@/lib/api/academic-periods'
import { type CopyPeriodFormValues, copyPeriodFormSchema } from '@/lib/schemas/academicPeriodSchema'

interface CopyPeriodDialogProps {
  sourcePeriod: AcademicPeriod
  open: boolean
  onOpenChange: (open: boolean) => void
  onCopied?: () => void
}

export const CopyPeriodDialog: React.FC<CopyPeriodDialogProps> = ({
  sourcePeriod,
  open,
  onOpenChange,
  onCopied,
}) => {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<CopyPeriodFormValues>({
    resolver: zodResolver(copyPeriodFormSchema),
    defaultValues: { name: '', startDate: '', endDate: '' },
  })

  const mutation = useMutation({
    mutationFn: (payload: CopyPeriodFormValues) => copyPeriod(sourcePeriod.id, payload),
    onSuccess: () => {
      reset()
      onOpenChange(false)
      onCopied?.()
      toast.success('Período copiado com sucesso.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível copiar o período.')
    },
  })

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
          <DialogTitle className="text-base font-semibold">Copiar Período</DialogTitle>
          <DialogDescription>
            Crie um novo período a partir de <strong>{sourcePeriod.name}</strong>. Feriados dentro do
            novo intervalo e grades de ocupação serão copiados.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="flex flex-col gap-5">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="copy-name">Nome do Novo Período *</Label>
            <Input
              id="copy-name"
              placeholder="Ex: 2024.2"
              aria-invalid={!!errors.name}
              {...register('name')}
            />
            <FieldError message={errors.name?.message} />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="copy-start">Data de Início *</Label>
              <Input
                id="copy-start"
                type="date"
                aria-invalid={!!errors.startDate}
                {...register('startDate')}
              />
              <FieldError message={errors.startDate?.message} />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="copy-end">Data de Fim *</Label>
              <Input
                id="copy-end"
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
              Copiar Período
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
