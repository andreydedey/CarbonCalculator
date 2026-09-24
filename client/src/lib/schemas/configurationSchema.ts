import { z } from 'zod'

export const OS_OPTIONS = [
  'Windows 10',
  'Windows 11',
  'Ubuntu 22.04 LTS',
  'Ubuntu 24.04 LTS',
  'Linux Mint',
  'Fedora',
  'macOS',
  'Outro',
] as const

export const configurationFormSchema = z.object({
  equipmentModelId: z.string().min(1, 'Selecione um computador'),
  operatingSystem: z.string().trim().min(1, 'Selecione o sistema operacional'),
  monitorId: z.string().optional().default(''),
})

export type ConfigurationFormValues = z.infer<typeof configurationFormSchema>
