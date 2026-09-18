/**
 * Schema Zod do formulário de criação de instituição (US-001), usado pelo
 * `InstitutionForm` via `zodResolver` (ver TDD 02).
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não
 * importa módulos externos — nem mesmo o `zod` — para poder ser verificada
 * com `node --test` sem depender de `node_modules` instalado (mesma
 * convenção de `InstitutionContext.tsx`, `AppLayout.tsx` e
 * `InstitutionSwitcher.tsx`). O schema Zod, logo abaixo do marcador, reusa
 * exatamente essas funções — não é uma cópia da regra, é a regra real.
 */

/** As 27 unidades federativas do Brasil (26 estados + Distrito Federal). */
export const BRAZILIAN_STATES = [
  "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
  "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC",
  "SP", "SE", "TO",
] as const

export type BrazilianState = (typeof BRAZILIAN_STATES)[number]

/** @spec:AC-003 UF inválida é rejeitada — só aceita as 27 UFs oficiais. */
export function isBrazilianState(value: string): value is BrazilianState {
  return (BRAZILIAN_STATES as readonly string[]).includes(value)
}

export interface RawInstitutionFormInput {
  name: string
  acronym: string
  city: string
  state: string
  laboratoryName: string
}

export interface InstitutionFormFieldErrors {
  name?: string
  acronym?: string
  city?: string
  state?: string
  laboratoryName?: string
}

/**
 * Valida os campos do formulário de instituição + laboratório vinculado
 * (@spec:AC-001 dados válidos passam sem erros, @spec:AC-003 UF fora da
 * lista das 27 unidades federativas é rejeitada).
 */
export function validateInstitutionForm(
  input: RawInstitutionFormInput,
): InstitutionFormFieldErrors {
  const errors: InstitutionFormFieldErrors = {}

  if (input.name.trim().length === 0) {
    errors.name = "Nome é obrigatório"
  }
  if (input.acronym.trim().length === 0) {
    errors.acronym = "Sigla é obrigatória"
  }
  if (!isBrazilianState(input.state)) {
    errors.state = "UF inválida. Selecione uma das 27 unidades federativas."
  }
  if (input.laboratoryName.trim().length === 0) {
    errors.laboratoryName = "Nome do laboratório é obrigatório"
  }

  return errors
}

export interface NormalizedInstitutionForm {
  name: string
  acronym: string
  city?: string
  state: string
  laboratory: { name: string }
}

/**
 * Converte os valores brutos do formulário no payload esperado por
 * `createInstitution` (@spec:AC-001): aparas os campos de texto e
 * transforma cidade em branco em `undefined` (campo opcional).
 */
export function normalizeInstitutionForm(
  input: RawInstitutionFormInput,
): NormalizedInstitutionForm {
  const city = input.city.trim()
  return {
    name: input.name.trim(),
    acronym: input.acronym.trim(),
    city: city.length > 0 ? city : undefined,
    state: input.state,
    laboratory: { name: input.laboratoryName.trim() },
  }
}

// @pure-logic-boundary

import { z } from "zod"

export const institutionFormSchema = z
  .object({
    name: z.string(),
    acronym: z.string(),
    city: z.string(),
    state: z.string(),
    laboratoryName: z.string(),
  })
  .superRefine((data, ctx) => {
    const errors = validateInstitutionForm(data)
    for (const [field, message] of Object.entries(errors)) {
      if (message) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: [field], message })
      }
    }
  })

export type InstitutionFormValues = z.infer<typeof institutionFormSchema>
