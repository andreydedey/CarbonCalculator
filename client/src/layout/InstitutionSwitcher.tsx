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

import { useInstitution } from "@/context/InstitutionContext"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

export function InstitutionSwitcher({
  options = [],
}: {
  options?: readonly InstitutionOption[]
}) {
  const { institutionId, setInstitutionId } = useInstitution()

  function handleChange(value: string) {
    if (isValidInstitutionSelection(value, options)) {
      setInstitutionId(value)
    }
  }

  return (
    <Select value={institutionId ?? ""} onValueChange={handleChange}>
      <SelectTrigger aria-label="Instituição ativa">
        <SelectValue placeholder="Selecione uma instituição" />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.id} value={option.id}>
            {option.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
