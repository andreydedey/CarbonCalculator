import type { CreateLaboratoryPayload, Laboratory } from '@/lib/api/laboratories'
import type { LaboratoryFormValues } from '@/lib/schemas/laboratorySchema'

export type LaboratoryFormMode = 'create' | 'edit'

export function resolveLaboratoryFormMode(laboratory?: Laboratory): LaboratoryFormMode {
  return laboratory ? 'edit' : 'create'
}

export function buildLaboratoryPayload(values: LaboratoryFormValues): CreateLaboratoryPayload {
  return {
    name: values.name.trim(),
    ...(values.description?.trim() ? { description: values.description.trim() } : {}),
  }
}
