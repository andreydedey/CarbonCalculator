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
    : null

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-3" noValidate>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="laboratory-name" className="text-sm font-medium">
          Nome do laboratório
        </label>
        <input
          id="laboratory-name"
          className="h-9 rounded-lg border border-border bg-background px-2.5 text-sm"
          placeholder="Ex.: LABCOMP-01"
          aria-invalid={errors.name ? 'true' : 'false'}
          {...register('name')}
        />
        {errors.name && (
          <p role="alert" className="text-sm text-destructive">
            {errors.name.message}
          </p>
        )}
      </div>
      {submitError && (
        <p role="alert" className="text-sm text-destructive">
          {submitError}
        </p>
      )}
      <Button type="submit" disabled={mutation.isPending}>
        {mode === 'edit' ? 'Salvar alterações' : 'Criar laboratório'}
      </Button>
    </form>
  )
}
