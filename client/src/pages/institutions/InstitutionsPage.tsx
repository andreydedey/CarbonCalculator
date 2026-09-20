import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import type React from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
import { createInstitution } from '@/lib/api/institutions'
import { mapCreateInstitutionError } from '@/lib/institutions/mapCreateInstitutionError'
import {
  BRAZILIAN_STATES,
  type InstitutionFormValues,
  institutionFormSchema,
  normalizeInstitutionForm,
} from '@/lib/schemas/institutionSchema'

export const InstitutionsPage: React.FC = () => {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors },
  } = useForm<InstitutionFormValues>({
    resolver: zodResolver(institutionFormSchema),
    defaultValues: { name: '', acronym: '', city: '', state: '', laboratoryName: '' },
  })

  const mutation = useMutation({
    mutationFn: createInstitution,
    onSuccess: () => {
      reset()
      toast.success('Instituição criada.')
    },
    onError: (error) => {
      const mapped = mapCreateInstitutionError(error)
      if (mapped.field === 'root') {
        setError('root', { type: 'server', message: mapped.message })
      } else {
        setError(mapped.field, { type: 'server', message: mapped.message })
      }
    },
  })

  const rootError = errors.root?.message

  function onSubmit(values: InstitutionFormValues) {
    mutation.mutate(normalizeInstitutionForm(values))
  }

  return (
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <p className="text-sm text-muted-foreground">Cadastro &rsaquo; Instituições</p>
          <h1 className="font-heading text-2xl font-bold">Nova Instituição</h1>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Dados da Instituição</CardTitle>
            <CardDescription>
              Informações gerais da universidade ou centro de pesquisa
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="name">Nome da Instituição</Label>
                <Input
                  id="name"
                  placeholder="Ex: Universidade Federal do Pará"
                  aria-invalid={!!errors.name}
                  {...register('name')}
                />
                <FieldError message={errors.name?.message} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="acronym">Sigla</Label>
                <Input
                  id="acronym"
                  placeholder="Ex: UFPA"
                  aria-invalid={!!errors.acronym}
                  {...register('acronym')}
                />
                <FieldError message={errors.acronym?.message} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="city">Cidade</Label>
                <Input
                  id="city"
                  placeholder="Ex: Belém"
                  aria-invalid={!!errors.city}
                  {...register('city')}
                />
                <FieldError message={errors.city?.message} />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label>UF</Label>
                <Controller
                  control={control}
                  name="state"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className="w-full" aria-invalid={!!errors.state}>
                        <SelectValue placeholder="Selecione" />
                      </SelectTrigger>
                      <SelectContent>
                        {BRAZILIAN_STATES.map((state) => (
                          <SelectItem key={state} value={state}>
                            {state}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                <FieldError message={errors.state?.message} />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Laboratório Vinculado</CardTitle>
            <CardDescription>Configure o primeiro laboratório desta instituição</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="laboratoryName">Nome do Laboratório</Label>
              <Input
                id="laboratoryName"
                placeholder="Ex: LABCOMP-01"
                aria-invalid={!!errors.laboratoryName}
                {...register('laboratoryName')}
              />
              <FieldError message={errors.laboratoryName?.message} />
            </div>
          </CardContent>
        </Card>

        <FieldError message={rootError} />

        <div className="flex justify-end gap-3">
          <Button type="submit" disabled={mutation.isPending}>
            Salvar Instituição
          </Button>
        </div>
      </form>
  )
}
