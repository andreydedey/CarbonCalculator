import { z } from 'zod'

export const EQUIPMENT_TYPES = ['Desktop', 'Notebook', 'All-in-One', 'Servidor'] as const

export const MEMORY_OPTIONS = [2, 4, 8, 16, 32, 64, 128] as const

const optionalPositiveInt = z.preprocess(
  (v) => (v === '' || v === undefined ? undefined : v),
  z.coerce.number().int().positive('Informe um valor positivo').optional(),
)

export const equipmentModelFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Informe o nome do modelo'),
    equipmentType: z.string().optional().default(''),
    processor: z.string().trim().optional().default(''),
    tdpWatts: optionalPositiveInt,
    coreCount: optionalPositiveInt,
    memoryGb: optionalPositiveInt,
    gpuModel: z.string().trim().optional().default(''),
    gpuTdpWatts: optionalPositiveInt,
    hasIntegratedScreen: z.boolean().default(false),
    description: z.string().trim().optional().default(''),
  })
  .refine((data) => !data.gpuModel || data.gpuTdpWatts, {
    message: 'Informe o TDP da GPU',
    path: ['gpuTdpWatts'],
  })

export type EquipmentModelFormValues = z.infer<typeof equipmentModelFormSchema>
