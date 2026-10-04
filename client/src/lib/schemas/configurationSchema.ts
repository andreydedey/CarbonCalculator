import { z } from 'zod'

export const configurationFormSchema = z.object({
  equipmentModelId: z.string().min(1, 'Selecione um computador'),
  operatingSystemId: z.string().min(1, 'Selecione o sistema operacional'),
  monitorId: z.string().optional().default(''),
})

export type ConfigurationFormValues = z.infer<typeof configurationFormSchema>
