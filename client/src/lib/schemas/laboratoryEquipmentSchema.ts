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

export const laboratoryEquipmentFormSchema = z.object({
  equipmentModelId: z.string().min(1, 'Selecione um modelo de computador'),
  operatingSystem: z.string().trim().min(1, 'Informe o sistema operacional'),
  monitorId: z.string().optional().default(''),
  quantity: z.coerce.number().int().min(1, 'A quantidade deve ser pelo menos 1'),
})

export type LaboratoryEquipmentFormValues = z.infer<typeof laboratoryEquipmentFormSchema>
