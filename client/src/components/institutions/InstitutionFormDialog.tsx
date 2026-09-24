import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import type React from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { isApiError } from '@/lib/api/client'
import { createInstitution, type Institution, updateInstitution } from '@/lib/api/institutions'
import { BRAZILIAN_STATES } from '@/lib/schemas/institutionSchema'

const baseFields = {
  name: z.string().min(1, 'Nome é obrigatório'),
  acronym: z.string().min(1, 'Sigla é obrigatória'),
  city: z.string(),
  state: z
    .string()
    .min(1, 'Selecione uma UF')
    .refine((val) => (BRAZILIAN_STATES as readonly string[]).includes(val), 'UF inválida.'),
}

const createSchema = z.object({
  ...baseFields,
  laboratoryName: z.string().min(1, 'Nome do laboratório é obrigatório'),
})

const editSchema = z.object(baseFields)

type CreateValues = z.infer<typeof createSchema>

interface InstitutionFormDialogProps {
  institution?: Institution
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
}

export const InstitutionFormDialog: React.FC<InstitutionFormDialogProps> = ({
  institution,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = institution ? 'edit' : 'create'

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isDirty },
  } = useForm<CreateValues>({
    resolver: zodResolver(mode === 'edit' ? editSchema : createSchema),
    values: {
      name: institution?.name ?? '',
      acronym: institution?.acronym ?? '',
      city: institution?.city ?? '',
      state: institution?.state ?? '',
      laboratoryName: '',
    },
  })

  const saveMutation = useMutation({
    mutationFn: (values: CreateValues) => {
      const city = values.city.trim()
      if (mode === 'edit' && institution) {
        return updateInstitution(institution.id, {
          name: values.name.trim(),
          acronym: values.acronym.trim(),
          city: city || undefined,
          state: values.state,
        })
      }
      return createInstitution({
        name: values.name.trim(),
        acronym: values.acronym.trim(),
        city: city || undefined,
        state: values.state,
        laboratory: { name: values.laboratoryName.trim() },
      })
    },
  })

  async function onSubmit(values: CreateValues) {
    try {
      await saveMutation.mutateAsync(values)
      reset()
      onOpenChange(false)
      onSaved?.()
      toast.success(mode === 'edit' ? 'Instituição atualizada.' : 'Instituição criada.')
    } catch (error) {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar a instituição.')
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      reset()
      saveMutation.reset()
    }
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[500px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Instituição' : 'Nova Instituição'}
          </DialogTitle>
          <DialogDescription>
            {mode === 'edit'
              ? 'Altere os dados da instituição.'
              : 'Cadastre uma nova instituição com seu primeiro laboratório.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="inst-name">Nome *</Label>
                <Input
                  id="inst-name"
                  placeholder="Ex: Universidade Federal do Pará"
                  aria-invalid={!!errors.name}
                  {...register('name')}
                />
                <FieldError message={errors.name?.message} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="inst-acronym">Sigla *</Label>
                <Input
                  id="inst-acronym"
                  placeholder="Ex: UFPA"
                  aria-invalid={!!errors.acronym}
                  {...register('acronym')}
                />
                <FieldError message={errors.acronym?.message} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="inst-city">Cidade</Label>
                <Input
                  id="inst-city"
                  placeholder="Ex: Belém"
                  aria-invalid={!!errors.city}
                  {...register('city')}
                />
                <FieldError message={errors.city?.message} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label>UF *</Label>
                <Controller
                  control={control}
                  name="state"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className="w-full" aria-invalid={!!errors.state}>
                        <SelectValue placeholder="Selecione" />
                      </SelectTrigger>
                      <SelectContent>
                        {BRAZILIAN_STATES.map((st) => (
                          <SelectItem key={st} value={st}>
                            {st}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                <FieldError message={errors.state?.message} />
              </div>
            </div>

            {mode === 'create' && (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="inst-lab">Primeiro Laboratório *</Label>
                <Input
                  id="inst-lab"
                  placeholder="Ex: LABCOMP-01"
                  aria-invalid={!!errors.laboratoryName}
                  {...register('laboratoryName')}
                />
                <FieldError message={errors.laboratoryName?.message} />
              </div>
            )}
          </div>

          <DialogFooter className="mx-0 mb-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={!isDirty || saveMutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Criar Instituição'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
