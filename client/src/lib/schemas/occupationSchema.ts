import { z } from 'zod'

/** Stations used by one class: at least 1 and, when known, at most the laboratory capacity. */
export function stationsField(capacity: number) {
  const base = z.coerce
    .number({ invalid_type_error: 'Informe o número de estações' })
    .int('Informe um número inteiro')
    .min(1, 'Use ao menos 1 estação')
  return capacity > 0 ? base.max(capacity, `O laboratório tem ${capacity} estações`) : base
}

export function stationsFormSchema(capacity: number) {
  return z.object({ stations: stationsField(capacity) })
}

export type StationsFormValues = { stations: number }

export const occurrenceChoices = ['grid', 'different', 'cancelled'] as const

export type OccurrenceChoice = (typeof occurrenceChoices)[number]

/** Stations are only validated when the class happened with a different number than the grid. */
export function occurrenceFormSchema(capacity: number) {
  return z
    .object({
      choice: z.enum(occurrenceChoices),
      stations: z.coerce.number(),
    })
    .superRefine((values, ctx) => {
      if (values.choice !== 'different') return
      const result = stationsField(capacity).safeParse(values.stations)
      if (!result.success) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['stations'],
          message: result.error.issues[0].message,
        })
      }
    })
}

export type OccurrenceFormValues = { choice: OccurrenceChoice; stations: number }

export function extraClassFormSchema(capacityByLab: Record<string, number>) {
  return z
    .object({
      laboratoryId: z.string().min(1, 'Selecione um laboratório'),
      shiftId: z.string().min(1, 'Selecione um turno'),
      slot: z.coerce.number().int().min(1, 'Selecione a aula'),
      stations: z.coerce.number(),
    })
    .superRefine((values, ctx) => {
      const result = stationsField(capacityByLab[values.laboratoryId] ?? 0).safeParse(
        values.stations,
      )
      if (!result.success) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['stations'],
          message: result.error.issues[0].message,
        })
      }
    })
}

export type ExtraClassFormValues = {
  laboratoryId: string
  shiftId: string
  slot: number
  stations: number
}
