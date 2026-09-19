/**
 * Formulário de criação de instituição (US-001): dois cards conforme o
 * design (`design/TCC_carbon_calculator.pen`, tela "2 – Cadastro de
 * Instituição") — "Dados da Instituição" e "Laboratório Vinculado".
 *
 * Divergência intencional do design (ADR-003): o card "Dados da
 * Instituição" no protótipo inclui um seletor de "Tipo de Rede Elétrica"
 * (SIN/Isolado), e o card de laboratório inclui "Nº de Estações", "Turno"
 * e "Aulas por Dia". Esses campos ficam fora deste formulário — a spec
 * (ASM-001) assume SIN para todos os laboratórios nesta fase, e os demais
 * campos pertencem a outros PRDs (ver spec.md, seção "Fora de escopo").
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não
 * importa módulos externos, para poder ser verificada com `node --test`
 * sem depender de `node_modules` instalado.
 */

export interface ApiErrorLike {
  status: number
  message: string
}

function isApiErrorLike(error: unknown): error is ApiErrorLike {
  return (
    typeof error === "object" &&
    error !== null &&
    "status" in error &&
    typeof (error as { status: unknown }).status === "number" &&
    "message" in error &&
    typeof (error as { message: unknown }).message === "string"
  )
}

export interface SubmitFormError {
  field: "acronym" | "root"
  message: string
}

/**
 * Traduz o erro de `createInstitution` num erro de campo do formulário.
 * Sigla duplicada (409) vira erro no campo Sigla, para o usuário corrigir
 * sem perder o restante dos dados preenchidos (@spec:AC-002). Qualquer
 * outro erro vira um aviso geral do formulário.
 */
export function mapCreateInstitutionError(error: unknown): SubmitFormError {
  if (isApiErrorLike(error) && error.status === 409) {
    return {
      field: "acronym",
      message: error.message || "Sigla já está em uso por outra instituição.",
    }
  }
  if (isApiErrorLike(error)) {
    return {
      field: "root",
      message: error.message || "Não foi possível salvar a instituição. Revise os dados.",
    }
  }
  return {
    field: "root",
    message: "Não foi possível salvar a instituição. Tente novamente.",
  }
}

// @pure-logic-boundary

import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation } from "@tanstack/react-query"
import {
  BRAZILIAN_STATES,
  institutionFormSchema,
  normalizeInstitutionForm,
  type InstitutionFormValues,
} from "@/lib/schemas/institutionSchema"
import { createInstitution, type Institution } from "@/lib/api/institutions"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { FieldError } from "@/components/ui/field-error"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

export interface InstitutionFormProps {
  onCreated?: (institution: Institution) => void
  onCancel?: () => void
}

export function InstitutionForm({ onCreated, onCancel }: InstitutionFormProps) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors },
  } = useForm<InstitutionFormValues>({
    resolver: zodResolver(institutionFormSchema),
    defaultValues: { name: "", acronym: "", city: "", state: "", laboratoryName: "" },
  })

  const mutation = useMutation({
    mutationFn: createInstitution,
    onSuccess: (institution) => {
      reset()
      onCreated?.(institution)
    },
    onError: (error) => {
      const mapped = mapCreateInstitutionError(error)
      if (mapped.field === "root") {
        setError("root", { type: "server", message: mapped.message })
      } else {
        setError(mapped.field, { type: "server", message: mapped.message })
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
                {...register("name")}
              />
              <FieldError message={errors.name?.message} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="acronym">Sigla</Label>
              <Input
                id="acronym"
                placeholder="Ex: UFPA"
                aria-invalid={!!errors.acronym}
                {...register("acronym")}
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
                {...register("city")}
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
          <CardDescription>
            Configure o primeiro laboratório desta instituição
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="laboratoryName">Nome do Laboratório</Label>
            <Input
              id="laboratoryName"
              placeholder="Ex: LABCOMP-01"
              aria-invalid={!!errors.laboratoryName}
              {...register("laboratoryName")}
            />
            <FieldError message={errors.laboratoryName?.message} />
          </div>
        </CardContent>
      </Card>

      <FieldError message={rootError} />

      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onCancel} disabled={mutation.isPending}>
          Cancelar
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          Salvar
        </Button>
      </div>
    </form>
  )
}
