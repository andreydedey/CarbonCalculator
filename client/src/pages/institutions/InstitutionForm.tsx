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

import { useForm } from "react-hook-form"
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

      <section className="flex flex-col gap-4 rounded-lg border border-border p-4">
        <div>
          <h2 className="text-lg font-semibold">Dados da Instituição</h2>
          <p className="text-sm text-muted-foreground">
            Informações gerais da universidade ou centro de pesquisa
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="flex flex-col gap-1">
            <label htmlFor="name" className="text-sm font-medium">
              Nome da Instituição
            </label>
            <input
              id="name"
              className="h-9 rounded-lg border border-border bg-background px-3 text-sm"
              placeholder="Ex: Universidade Federal do Pará"
              {...register("name")}
            />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="acronym" className="text-sm font-medium">
              Sigla
            </label>
            <input
              id="acronym"
              className="h-9 rounded-lg border border-border bg-background px-3 text-sm"
              placeholder="Ex: UFPA"
              {...register("acronym")}
            />
            {errors.acronym && (
              <p className="text-xs text-destructive">{errors.acronym.message}</p>
            )}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="flex flex-col gap-1">
            <label htmlFor="city" className="text-sm font-medium">
              Cidade
            </label>
            <input
              id="city"
              className="h-9 rounded-lg border border-border bg-background px-3 text-sm"
              placeholder="Ex: Belém"
              {...register("city")}
            />
            {errors.city && <p className="text-xs text-destructive">{errors.city.message}</p>}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="state" className="text-sm font-medium">
              UF
            </label>
            <select
              id="state"
              className="h-9 rounded-lg border border-border bg-background px-3 text-sm"
              defaultValue=""
              {...register("state")}
            >
              <option value="" disabled>
                Selecione
              </option>
              {BRAZILIAN_STATES.map((state) => (
                <option key={state} value={state}>
                  {state}
                </option>
              ))}
            </select>
            {errors.state && <p className="text-xs text-destructive">{errors.state.message}</p>}
          </div>
        </div>
      </section>

      <section className="flex flex-col gap-4 rounded-lg border border-border p-4">
        <div>
          <h2 className="text-lg font-semibold">Laboratório Vinculado</h2>
          <p className="text-sm text-muted-foreground">
            Configure o primeiro laboratório desta instituição
          </p>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="laboratoryName" className="text-sm font-medium">
            Nome do Laboratório
          </label>
          <input
            id="laboratoryName"
            className="h-9 rounded-lg border border-border bg-background px-3 text-sm"
            placeholder="Ex: LABCOMP-01"
            {...register("laboratoryName")}
          />
          {errors.laboratoryName && (
            <p className="text-xs text-destructive">{errors.laboratoryName.message}</p>
          )}
        </div>
      </section>

      {rootError && <p className="text-sm text-destructive">{rootError}</p>}

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
