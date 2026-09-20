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
import { Textarea } from '@/components/ui/textarea'
import { ApiError } from '@/lib/api/client'
import type { CreateLaboratoryPayload } from '@/lib/api/laboratories'
import { createLaboratory, type Laboratory, updateLaboratory } from '@/lib/api/laboratories'
import { type LaboratoryFormValues, laboratoryFormSchema } from '@/lib/schemas/laboratorySchema'

interface LaboratoryFormProps {
  laboratory?: Laboratory
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: (laboratory: Laboratory) => void
}

export const LaboratoryForm: React.FC<LaboratoryFormProps> = ({
  laboratory,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = laboratory ? 'edit' : 'create'
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<LaboratoryFormValues>({
    resolver: zodResolver(laboratoryFormSchema),
    defaultValues: { name: laboratory?.name ?? '', description: laboratory?.description ?? '' },
  })

  const mutation = useMutation({
    mutationFn: (payload: CreateLaboratoryPayload) =>
      mode === 'edit' && laboratory
        ? updateLaboratory(laboratory.id, payload)
        : createLaboratory(payload),
    onSuccess: (saved) => {
      reset()
      onOpenChange(false)
      onSaved?.(saved)
      toast.success(mode === 'edit' ? 'Laboratório atualizado.' : 'Laboratório criado.')
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : 'Não foi possível salvar o laboratório.',
      )
    },
  })

  function onSubmit(values: LaboratoryFormValues) {
    const payload: CreateLaboratoryPayload = {
      name: values.name.trim(),
      ...(values.description?.trim() ? { description: values.description.trim() } : {}),
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
      <DialogContent className="sm:max-w-[560px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Laboratório' : 'Novo Laboratório'}
          </DialogTitle>
          <DialogDescription>
            Informe os dados básicos e vincule os equipamentos deste laboratório.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="laboratory-name">Nome do Laboratório *</Label>
              <Input
                id="laboratory-name"
                placeholder="Ex: Laboratório de Computação 01"
                aria-invalid={!!errors.name}
                {...register('name')}
              />
              <FieldError message={errors.name?.message} />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="laboratory-description">Descrição</Label>
              <Textarea
                id="laboratory-description"
                placeholder="Descrição do laboratório (opcional)"
                rows={3}
                {...register('description')}
              />
            </div>

            <div className="h-px bg-border" />

            <div className="flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold">Equipamentos do Laboratório</span>
                <Button type="button" variant="outline" size="sm" disabled>
                  Vincular Equipamento
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                Selecione os modelos de equipamento cadastrados na instituição e informe a
                quantidade em uso neste laboratório.
              </p>
              <div className="rounded-lg border">
                <div className="flex items-center justify-center px-4 py-4">
                  <p className="text-xs text-muted-foreground italic">
                    Use &ldquo;Vincular Equipamento&rdquo; para adicionar modelos da instituição
                  </p>
                </div>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Criar Laboratório'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
