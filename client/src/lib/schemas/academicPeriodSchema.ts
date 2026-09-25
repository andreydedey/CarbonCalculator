import { z } from 'zod'

export const academicPeriodFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Informe o nome do período'),
    startDate: z.string().min(1, 'Informe a data de início'),
    endDate: z.string().min(1, 'Informe a data de fim'),
  })
  .refine((data) => data.endDate > data.startDate, {
    message: 'Data final deve ser posterior à data inicial',
    path: ['endDate'],
  })

export type AcademicPeriodFormValues = z.infer<typeof academicPeriodFormSchema>

export const copyPeriodFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Informe o nome do novo período'),
    startDate: z.string().min(1, 'Informe a data de início'),
    endDate: z.string().min(1, 'Informe a data de fim'),
  })
  .refine((data) => data.endDate > data.startDate, {
    message: 'Data final deve ser posterior à data inicial',
    path: ['endDate'],
  })

export type CopyPeriodFormValues = z.infer<typeof copyPeriodFormSchema>
