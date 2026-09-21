import { z } from 'zod'

export const inviteSchema = z.object({
  email: z.string().min(1, 'Email é obrigatório').email('Email inválido'),
  role: z.enum(['MANAGER', 'RESEARCHER']),
})

export type InviteFormData = z.infer<typeof inviteSchema>
