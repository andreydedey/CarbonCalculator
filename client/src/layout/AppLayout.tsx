/**
 * Layout principal da aplicação (US-004): barra lateral, cabeçalho com o
 * seletor de instituição e área de conteúdo.
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não usa
 * JSX, para poder ser verificada com `node --test` sem bundler.
 */

export type LayoutView = "blocked" | "content"

/**
 * Decide se o layout pode renderizar o conteúdo protegido por instituição.
 * Enquanto nenhuma instituição estiver identificada, nenhuma tela que
 * dispararia requisições de dados é montada — o front-end nunca chega a
 * pedir dados sem o contexto de instituição (@spec:AC-009).
 */
export function resolveLayoutView(hasInstitution: boolean): LayoutView {
  return hasInstitution ? "content" : "blocked"
}

export interface NavItem {
  label: string
  href: string
}

export const NAV_ITEMS: readonly NavItem[] = [
  { label: "Instituições", href: "/instituicoes" },
  { label: "Laboratórios", href: "/laboratorios" },
]

// @pure-logic-boundary

import { useQuery } from "@tanstack/react-query"
import type { ReactNode } from "react"
import { useInstitution } from "@/context/InstitutionContext"
import { InstitutionSwitcher } from "@/layout/InstitutionSwitcher"
import { listInstitutions } from "@/lib/api/institutions"

export function AppLayout({ children }: { children: ReactNode }) {
  const { hasInstitution } = useInstitution()
  const view = resolveLayoutView(hasInstitution)

  const { data: institutions = [] } = useQuery({
    queryKey: ["institutions"],
    queryFn: listInstitutions,
  })

  return (
    <div className="flex min-h-svh">
      <aside className="w-56 shrink-0 border-r border-border bg-muted/30 p-4">
        <p className="font-heading text-sm font-semibold">Carbon Calculator</p>
        <nav className="mt-6 flex flex-col gap-1">
          {NAV_ITEMS.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="rounded-lg px-2.5 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              {item.label}
            </a>
          ))}
        </nav>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-border px-4 py-3">
          <span className="text-sm text-muted-foreground">Instituição ativa</span>
          <InstitutionSwitcher options={institutions.map((i) => ({ id: i.id, name: i.name }))} />
        </header>
        <main className="flex-1 p-4">
          {view === "content" ? (
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
