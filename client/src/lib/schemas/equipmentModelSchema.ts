import { z } from 'zod'

export const equipmentModelFormSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do modelo'),
  processor: z.string().trim().optional().default(''),
  memoryGb: z.coerce.number().int().positive('Informe um valor positivo').optional(),
  hasDedicatedGpu: z.boolean().optional().default(false),
  gpuModel: z.string().trim().optional().default(''),
  monitorName: z.string().trim().optional().default(''),
  monitorSizeInches: z.coerce.number().positive('Informe um valor positivo').optional(),
  monitorResolution: z.string().trim().optional().default(''),
})

export type EquipmentModelFormValues = z.infer<typeof equipmentModelFormSchema>
