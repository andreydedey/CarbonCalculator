import { z } from 'zod'

export const emissionFactorFormSchema = z.object({
  year: z.coerce.number().int().min(2000).max(2100),
  month: z.coerce.number().int().min(1).max(12),
  value: z.coerce.number().positive('O valor deve ser maior que zero'),
  source: z.string().min(1, 'A fonte é obrigatória'),
})

export type EmissionFactorFormValues = z.infer<typeof emissionFactorFormSchema>
