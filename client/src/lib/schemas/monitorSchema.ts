import { z } from 'zod'

export const monitorFormSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do monitor'),
  watts: z.coerce.number().int().positive('Informe um valor positivo').optional(),
})

export type MonitorFormValues = z.infer<typeof monitorFormSchema>
