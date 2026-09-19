/**
 * Formulário de criação/edição de laboratório (US-002, AC-004, AC-005).
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não usa
 * JSX, para poder ser verificada com `node --test` sem bundler.
 */

import type { CreateLaboratoryPayload, Laboratory } from '@/lib/api/laboratories'
import type { LaboratoryFormValues } from '@/lib/schemas/laboratorySchema'

export type LaboratoryFormMode = 'create' | 'edit'

/** Cria ou edita, dependendo se já existe um laboratório carregado. */
export function resolveLaboratoryFormMode(laboratory?: Laboratory): LaboratoryFormMode {
  return laboratory ? 'edit' : 'create'
}

/**
 * Traduz os valores já validados do formulário no payload aceito pela API
 * (@spec:AC-004). O nome chega confiável do schema (que já faz trim e
 * exige ao menos um caractere), então este payload nunca é enviado vazio.
 */
export function buildLaboratoryPayload(values: LaboratoryFormValues): CreateLaboratoryPayload {
  return { name: values.name.trim() }
}

// @pure-logic-boundary

import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/client'
import { createLaboratory, updateLaboratory } from '@/lib/api/laboratories'
import { laboratoryFormSchema } from '@/lib/schemas/laboratorySchema'

export function LaboratoryForm({
  laboratory,
  onSaved,
}: {
  laboratory?: Laboratory
  onSaved?: (laboratory: Laboratory) => void
}) {
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
