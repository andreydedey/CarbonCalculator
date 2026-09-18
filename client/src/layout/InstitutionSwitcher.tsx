/**
 * Seletor de instituição ativa, exibido no cabeçalho do layout (US-004).
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não usa
 * JSX, para poder ser verificada com `node --test` sem bundler.
 */

export interface InstitutionOption {
  id: string
  name: string
}

/**
 * Só aceita trocar para uma instituição que de fato está na lista de
 * opções conhecidas. Isso impede que o seletor produza um id arbitrário e
 * envie um `X-Institution-Id` que não corresponde a nenhuma instituição
 * real do usuário, o que quebraria o isolamento por RLS (@spec:AC-008).
 */
export function isValidInstitutionSelection(
  institutionId: string,
  options: readonly InstitutionOption[],
): boolean {
  return options.some((option) => option.id === institutionId)
}

// @pure-logic-boundary

import type { ChangeEvent } from "react"
import { useInstitution } from "@/context/InstitutionContext"

export function InstitutionSwitcher({
  options = [],
}: {
  options?: readonly InstitutionOption[]
}) {
  const { institutionId, setInstitutionId } = useInstitution()

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    const next = event.target.value
    if (isValidInstitutionSelection(next, options)) {
      setInstitutionId(next)
    }
  }

  return (
    <select
      aria-label="Instituição ativa"
      className="h-8 rounded-lg border border-border bg-background px-2 text-sm"
      value={institutionId ?? ""}
      onChange={handleChange}
    >
      <option value="" disabled>
        Selecione uma instituição
      </option>
      {options.map((option) => (
        <option key={option.id} value={option.id}>
          {option.name}
        </option>
      ))}
    </select>
  )
}
