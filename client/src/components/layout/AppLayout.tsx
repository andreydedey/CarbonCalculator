import { useQuery } from '@tanstack/react-query'
import type React from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { InstitutionSwitcher } from '@/components/layout/InstitutionSwitcher'
import { useInstitution } from '@/context/InstitutionContext'
import { listInstitutions } from '@/lib/api/institutions'
import { NAV_ITEMS, resolveLayoutView } from '@/lib/layout/resolveLayoutView'

export const AppLayout: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { hasInstitution } = useInstitution()
  const view = resolveLayoutView(hasInstitution)

  const { data: institutions = [] } = useQuery({
    queryKey: ['institutions'],
    queryFn: listInstitutions,
  })

  return (
    <div className="flex min-h-svh">
      <aside className="w-56 shrink-0 border-r border-border bg-muted/30 p-4">
        <p className="font-heading text-sm font-semibold">Carbon Calculator</p>
        <nav className="mt-6 flex flex-col gap-1">
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.href}
              to={item.href}
              className="rounded-lg px-2.5 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-border px-4 py-3">
          <span className="text-sm text-muted-foreground">Instituição ativa</span>
          <InstitutionSwitcher options={institutions.map((i) => ({ id: i.id, name: i.name }))} />
        </header>
        <main className="flex-1 p-4">
          {view === 'content' ? (
            children
          ) : (
            <p className="text-sm text-muted-foreground">
              Selecione uma instituição para continuar.
            </p>
          )}
        </main>
      </div>
    </div>
  )
}
