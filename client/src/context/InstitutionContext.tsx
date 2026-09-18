/**
 * Contexto de instituição ativa (US-004).
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não depende
 * de JSX nem de nenhum módulo externo — de propósito, para poder ser
 * verificada isoladamente com `node --test`, já que este projeto ainda não
 * tem um bundler instalado para transformar JSX em testes.
 */

export const INSTITUTION_STORAGE_KEY = "carbon-calculator:institution-id"

export interface StorageLike {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

/**
 * Lê o id da instituição ativa persistido. Uma string vazia ou só com
 * espaços é tratada como "nenhuma instituição identificada"
 * (@spec:AC-009).
 */
export function readStoredInstitutionId(storage: StorageLike): string | null {
  const raw = storage.getItem(INSTITUTION_STORAGE_KEY)
  return raw && raw.trim().length > 0 ? raw : null
}

/**
 * Persiste o id da instituição ativa. Passar `null` (ou string vazia) limpa
 * o valor salvo, devolvendo a aplicação ao estado "sem instituição
 * identificada" (@spec:AC-009).
 */
export function writeStoredInstitutionId(
  storage: StorageLike,
  institutionId: string | null,
): void {
  if (institutionId && institutionId.trim().length > 0) {
    storage.setItem(INSTITUTION_STORAGE_KEY, institutionId)
  } else {
    storage.removeItem(INSTITUTION_STORAGE_KEY)
  }
}

export interface InstitutionContextValue {
  /** Id da instituição ativa, ou `null` enquanto nenhuma foi selecionada. */
  institutionId: string | null
  /**
   * `false` enquanto nenhuma instituição estiver identificada. Telas e
   * chamadas que dependem de instituição devem recusar-se a disparar
   * requisições nesse estado — o front-end nunca deve tentar uma chamada
   * sem o header `X-Institution-Id` (@spec:AC-009). O contexto guarda um
   * único id por vez, então nunca há mistura de dados de duas instituições
   * na mesma sessão de navegação (@spec:AC-008).
   */
  hasInstitution: boolean
  setInstitutionId: (institutionId: string) => void
  clearInstitutionId: () => void
}

// @pure-logic-boundary

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react"

export const InstitutionContext = createContext<InstitutionContextValue | undefined>(
  undefined,
)

export function InstitutionProvider({ children }: { children: ReactNode }) {
  const [institutionId, setInstitutionIdState] = useState<string | null>(() =>
    typeof window === "undefined" ? null : readStoredInstitutionId(window.localStorage),
  )

  useEffect(() => {
    if (typeof window === "undefined") return
    writeStoredInstitutionId(window.localStorage, institutionId)
  }, [institutionId])

  const setInstitutionId = useCallback((next: string) => {
    setInstitutionIdState(next)
  }, [])

  const clearInstitutionId = useCallback(() => {
    setInstitutionIdState(null)
  }, [])

  const value = useMemo<InstitutionContextValue>(
    () => ({
      institutionId,
      hasInstitution: institutionId !== null,
      setInstitutionId,
      clearInstitutionId,
    }),
    [institutionId, setInstitutionId, clearInstitutionId],
  )

  return (
    <InstitutionContext.Provider value={value}>{children}</InstitutionContext.Provider>
  )
}

/** Acesso ao contexto de instituição ativa. Lança erro fora do provider. */
export function useInstitution(): InstitutionContextValue {
  const context = useContext(InstitutionContext)
  if (!context) {
    throw new Error("useInstitution deve ser usado dentro de um InstitutionProvider")
  }
  return context
}
