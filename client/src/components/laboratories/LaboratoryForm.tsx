import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import type React from 'react'
import { useForm } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/client'
import type { CreateLaboratoryPayload } from '@/lib/api/laboratories'
import { createLaboratory, type Laboratory, updateLaboratory } from '@/lib/api/laboratories'
import {
  buildLaboratoryPayload,
  resolveLaboratoryFormMode,
} from '@/lib/laboratories/buildLaboratoryPayload'
import { type LaboratoryFormValues, laboratoryFormSchema } from '@/lib/schemas/laboratorySchema'

interface LaboratoryFormProps {
  laboratory?: Laboratory
  onSaved?: (laboratory: Laboratory) => void
}

export const LaboratoryForm: React.FC<LaboratoryFormProps> = ({ laboratory, onSaved }) => {
  const mode = resolveLaboratoryFormMode(laboratory)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LaboratoryFormValues>({
    resolver: zodResolver(laboratoryFormSchema),
    defaultValues: { name: laboratory?.name ?? '' },
  })

  const mutation = useMutation({
    mutationFn: (payload: CreateLaboratoryPayload) =>
      mode === 'edit' && laboratory
        ? updateLaboratory(laboratory.id, payload)
        : createLaboratory(payload),
    onSuccess: (saved) => onSaved?.(saved),
  })

  function onSubmit(values: LaboratoryFormValues) {
    mutation.mutate(buildLaboratoryPayload(values))
  }

  const submitError = mutation.error
    ? mutation.error instanceof ApiError
      ? mutation.error.message
      : 'Não foi possível salvar o laboratório.'
    : undefined

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3" noValidate>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="laboratory-name">Nome do laboratório</Label>
        <Input
          id="laboratory-name"
          placeholder="Ex.: LABCOMP-01"
          aria-invalid={!!errors.name}
          {...register('name')}
        />
        <FieldError message={errors.name?.message} />
      </div>
      <FieldError message={submitError} />
      <Button type="submit" disabled={mutation.isPending}>
        {mode === 'edit' ? 'Salvar alterações' : 'Criar laboratório'}
      </Button>
    </form>
  )
}
