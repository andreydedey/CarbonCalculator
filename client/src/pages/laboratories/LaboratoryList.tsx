/**
 * Lista de laboratórios da instituição ativa (US-003).
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não usa
 * JSX, para poder ser verificada com `node --test` sem bundler.
 */

import type { Laboratory, ListLaboratoriesOptions } from "@/lib/api/laboratories"

export interface LaboratoryViewModel {
  id: string
  name: string
  active: boolean
  statusLabel: string
}

/**
 * Decide os parâmetros de consulta a partir do estado do toggle "mostrar
 * inativos". Por padrão (`showInactive = false`) nenhum parâmetro é
 * enviado, e a listagem traz apenas laboratórios ativos (@spec:AC-006).
 * Quando o toggle é ligado, `includeInactive` é enviado para trazer também
 * os inativos junto com os ativos (@spec:AC-007).
 */
export function buildListQuery(showInactive: boolean): ListLaboratoriesOptions {
  return showInactive ? { includeInactive: true } : {}
}

/** Mapeia um laboratório da API para o modelo exibido nos cards da lista. */
export function toViewModel(laboratory: Laboratory): LaboratoryViewModel {
  return {
    id: laboratory.id,
    name: laboratory.name,
    active: laboratory.active,
    statusLabel: laboratory.active ? "Ativo" : "Inativo",
  }
}

// @pure-logic-boundary

import { useCallback, useEffect, useState } from "react"
import { listLaboratories } from "@/lib/api/laboratories"

export function LaboratoryList() {
  const [showInactive, setShowInactive] = useState(false)
  const [laboratories, setLaboratories] = useState<LaboratoryViewModel[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const loadLaboratories = useCallback(async (includeInactive: boolean) => {
    setIsLoading(true)
    try {
      const result = await listLaboratories(buildListQuery(includeInactive))
      setLaboratories(result.map(toViewModel))
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadLaboratories(showInactive)
  }, [showInactive, loadLaboratories])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="font-heading text-lg font-semibold">Laboratórios</h1>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            checked={showInactive}
            onChange={(event) => setShowInactive(event.target.checked)}
          />
          Mostrar inativos
        </label>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : laboratories.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum laboratório encontrado.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {laboratories.map((laboratory) => (
            <div
              key={laboratory.id}
              className="rounded-lg border border-border bg-background p-4"
            >
              <div className="flex items-center justify-between">
                <p className="font-medium">{laboratory.name}</p>
                <span
                  className={
                    laboratory.active
                      ? "rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary"
                      : "rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
                  }
                >
                  {laboratory.statusLabel}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
