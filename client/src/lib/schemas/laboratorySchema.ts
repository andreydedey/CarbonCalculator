import { z } from 'zod'

export const laboratoryFormSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do laboratório'),
})

export type LaboratoryFormValues = z.infer<typeof laboratoryFormSchema>
