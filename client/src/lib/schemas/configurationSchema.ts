import { z } from 'zod'

export const OS_OPTIONS = ['Windows 10', 'Windows 11', 'Linux', 'macOS'] as const

export const configurationFormSchema = z.object({
  equipmentModelId: z.string().min(1, 'Selecione um computador'),
  operatingSystem: z.string().trim().min(1, 'Selecione o sistema operacional'),
  monitorId: z.string().optional().default(''),
})

export type ConfigurationFormValues = z.infer<typeof configurationFormSchema>
