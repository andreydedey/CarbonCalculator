import { z } from 'zod'

export const EQUIPMENT_TYPES = ['Desktop', 'Notebook', 'All-in-One', 'Servidor'] as const

export const MEMORY_OPTIONS = [2, 4, 8, 16, 32, 64, 128] as const

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

export const equipmentModelFormSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do modelo'),
  equipmentType: z.string().optional().default(''),
  processor: z.string().trim().optional().default(''),
  tdpWatts: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  coreCount: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  memoryGb: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  monitorName: z.string().trim().optional().default(''),
  monitorWatts: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  operatingSystem: z.string().optional().default(''),
  description: z.string().trim().optional().default(''),
})

export type EquipmentModelFormValues = z.infer<typeof equipmentModelFormSchema>
