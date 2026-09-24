import { z } from 'zod'

export const linkEquipmentFormSchema = z.object({
  configurationId: z.string().min(1, 'Selecione uma configuração'),
  quantity: z.coerce.number().int().min(1, 'A quantidade deve ser pelo menos 1'),
})

export type LinkEquipmentFormValues = z.infer<typeof linkEquipmentFormSchema>
