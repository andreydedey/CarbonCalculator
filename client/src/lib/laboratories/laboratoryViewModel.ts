import type { Laboratory, ListLaboratoriesOptions } from '@/lib/api/laboratories'

export interface LaboratoryViewModel {
  id: string
  name: string
  active: boolean
  statusLabel: string
}

export function buildListQuery(showInactive: boolean): ListLaboratoriesOptions {
  return showInactive ? { includeInactive: true } : {}
}

export function toViewModel(laboratory: Laboratory): LaboratoryViewModel {
  return {
    id: laboratory.id,
    name: laboratory.name,
    active: laboratory.active,
    statusLabel: laboratory.active ? 'Operando' : 'Inativo',
  }
}
