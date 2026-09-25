import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react'
import { useAuth } from '@/context/AuthContext'
import {
  readStoredInstitutionId,
  writeStoredInstitutionId,
} from '@/lib/context/institutionStorage'

export interface InstitutionContextValue {
  institutionId: string | null
  hasInstitution: boolean
  setInstitutionId: (institutionId: string) => void
  clearInstitutionId: () => void
}

export const InstitutionContext = createContext<InstitutionContextValue | undefined>(undefined)

function resolveInstitutionId(
  stored: string | null,
  memberships: { institutionId: string; status: string }[] | undefined,
): string | null {
  if (!memberships) return stored
  const active = memberships.filter((m) => m.status === 'ACTIVE')
  if (stored && active.some((m) => m.institutionId === stored)) return stored
  return active[0]?.institutionId ?? null
}

export const InstitutionProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { user } = useAuth()
  const [rawId, setRawId] = useState<string | null>(readStoredInstitutionId)

  const institutionId = useMemo(
    () => resolveInstitutionId(rawId, user?.institutions),
    [rawId, user?.institutions],
  )

  // Keep cookie in sync — pure derivation, write on every render where it changed
  useMemo(() => writeStoredInstitutionId(institutionId), [institutionId])

  const setInstitutionId = useCallback((next: string) => setRawId(next), [])
  const clearInstitutionId = useCallback(() => setRawId(null), [])

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
