import { z } from 'zod'

export const TARGET_TYPE_OPTIONS = [
  { value: 'COMPUTER' as const, label: 'Computador' },
  { value: 'MONITOR' as const, label: 'Monitor' },
  { value: 'COMBINED' as const, label: 'Conjunto (computador + monitor)' },
]

export const consumptionMeasurementFormSchema = z
  .object({
    targetType: z.enum(['COMPUTER', 'MONITOR', 'COMBINED'], {
      required_error: 'Selecione o tipo de alvo',
    }),
    equipmentModelId: z.string().optional().default(''),
    operatingSystemId: z.string().optional().default(''),
    monitorId: z.string().optional().default(''),
    averageWatts: z.coerce.number().positive('Deve ser maior que zero'),
    durationMinutes: z.coerce.number().int().positive('Deve ser maior que zero'),
    readingIntervalMinutes: z.coerce.number().int().positive().optional().or(z.literal('')),
    measurementDate: z.string().min(1, 'Data é obrigatória'),
    conditions: z.string().optional().default(''),
  })
  .superRefine((data, ctx) => {
    if (data.targetType === 'COMPUTER' || data.targetType === 'COMBINED') {
      if (!data.equipmentModelId) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Selecione um computador',
          path: ['equipmentModelId'],
        })
      }
      if (!data.operatingSystemId) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Selecione o sistema operacional',
          path: ['operatingSystemId'],
        })
      }
    }
    if (data.targetType === 'MONITOR' || data.targetType === 'COMBINED') {
      if (!data.monitorId) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Selecione um monitor',
          path: ['monitorId'],
        })
      }
    }
  })

export type ConsumptionMeasurementFormValues = z.infer<typeof consumptionMeasurementFormSchema>
