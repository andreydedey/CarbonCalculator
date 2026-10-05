import { useAuth } from '@/context/AuthContext'
import { useInstitution } from '@/context/InstitutionContext'

/** Whether the user can change data of the active institution (MANAGER or platform admin). */
export function useCanManage(): boolean {
  const { user } = useAuth()
  const { institutionId } = useInstitution()
  if (user?.admin) return true
  return (
    user?.institutions?.some(
      (m) => m.institutionId === institutionId && m.status === 'ACTIVE' && m.role === 'MANAGER',
    ) ?? false
  )
}
