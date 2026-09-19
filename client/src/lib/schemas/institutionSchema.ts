import { z } from 'zod'

export const BRAZILIAN_STATES = [
  'AC',
  'AL',
  'AP',
  'AM',
  'BA',
  'CE',
  'DF',
  'ES',
  'GO',
  'MA',
  'MT',
  'MS',
  'MG',
  'PA',
  'PB',
  'PR',
  'PE',
  'PI',
  'RJ',
  'RN',
  'RS',
  'RO',
  'RR',
  'SC',
  'SP',
  'SE',
  'TO',
] as const

export type BrazilianState = (typeof BRAZILIAN_STATES)[number]

export const institutionFormSchema = z.object({
  name: z.string().min(1, 'Nome é obrigatório'),
  acronym: z.string().min(1, 'Sigla é obrigatória'),
  city: z.string(),
  state: z
    .string()
    .min(1, 'Selecione uma UF')
    .refine(
      (val) => (BRAZILIAN_STATES as readonly string[]).includes(val),
      'UF inválida. Selecione uma das 27 unidades federativas.',
    ),
  laboratoryName: z.string().min(1, 'Nome do laboratório é obrigatório'),
})

export type InstitutionFormValues = z.infer<typeof institutionFormSchema>

export interface NormalizedInstitutionForm {
  name: string
  acronym: string
  city?: string
  state: string
  laboratory: { name: string }
}

export function normalizeInstitutionForm(input: InstitutionFormValues): NormalizedInstitutionForm {
  const city = input.city.trim()
  return {
    name: input.name.trim(),
    acronym: input.acronym.trim(),
    city: city.length > 0 ? city : undefined,
    state: input.state,
    laboratory: { name: input.laboratoryName.trim() },
  }
}
