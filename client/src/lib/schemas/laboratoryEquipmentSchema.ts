import { z } from 'zod'

export const laboratoryEquipmentFormSchema = z.object({
  equipmentModelId: z.string().min(1, 'Selecione um modelo de equipamento'),
  operatingSystem: z.string().trim().min(1, 'Informe o sistema operacional'),
  quantity: z.coerce.number().int().min(1, 'A quantidade deve ser pelo menos 1'),
})

export type LaboratoryEquipmentFormValues = z.infer<typeof laboratoryEquipmentFormSchema>
