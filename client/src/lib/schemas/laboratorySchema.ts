/**
 * Schema do formulário de laboratório (US-002). O único campo obrigatório é
 * o nome (ASM-002) — um nome só com espaços em branco não conta como
 * informado (@spec:AC-005).
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não
 * importa nenhum pacote externo, para poder ser verificada com
 * `node --test` mesmo sem `node_modules` instalado.
 */

export type LaboratoryNameValidation =
  | { valid: true; value: string }
  | { valid: false; error: string }

export const LABORATORY_NAME_REQUIRED_ERROR = 'Informe o nome do laboratório'

/**
 * Valida o nome do laboratório: é o único campo obrigatório do formulário,
 * e um valor só com espaços em branco não conta como informado
 * (@spec:AC-004, @spec:AC-005).
 */
export function validateLaboratoryName(name: string): LaboratoryNameValidation {
  const trimmed = name.trim()
  if (trimmed.length === 0) {
    return { valid: false, error: LABORATORY_NAME_REQUIRED_ERROR }
  }
  return { valid: true, value: trimmed }
}

// @pure-logic-boundary

import { z } from 'zod'

export const laboratoryFormSchema = z.object({
  name: z
    .string()
    .transform((value) => value.trim())
    .refine((value) => validateLaboratoryName(value).valid, LABORATORY_NAME_REQUIRED_ERROR),
})

export type LaboratoryFormValues = z.infer<typeof laboratoryFormSchema>
