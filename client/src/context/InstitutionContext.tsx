import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { readStoredInstitutionId, writeStoredInstitutionId } from '@/lib/context/institutionStorage'

export interface InstitutionContextValue {
  institutionId: string | null
  hasInstitution: boolean
  setInstitutionId: (institutionId: string) => void
  clearInstitutionId: () => void
}

export const InstitutionContext = createContext<InstitutionContextValue | undefined>(undefined)

export const InstitutionProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [institutionId, setInstitutionIdState] = useState<string | null>(() =>
    typeof window === 'undefined' ? null : readStoredInstitutionId(window.localStorage),
  )

  useEffect(() => {
    if (typeof window === 'undefined') return
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

  return <InstitutionContext.Provider value={value}>{children}</InstitutionContext.Provider>
}

export function useInstitution(): InstitutionContextValue {
  const context = useContext(InstitutionContext)
  if (!context) {
    throw new Error('useInstitution deve ser usado dentro de um InstitutionProvider')
  }
  return context
}
