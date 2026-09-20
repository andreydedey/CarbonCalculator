import { useQuery } from '@tanstack/react-query'
import { Building2, FlaskConical } from 'lucide-react'
import type React from 'react'
import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { InstitutionSwitcher } from '@/components/layout/InstitutionSwitcher'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { useInstitution } from '@/context/InstitutionContext'
import { listInstitutions } from '@/lib/api/institutions'
import { resolveLayoutView } from '@/lib/layout/resolveLayoutView'

const NAV_ITEMS = [
  { label: 'Instituições', href: '/institutions', icon: Building2 },
  { label: 'Laboratórios', href: '/laboratories', icon: FlaskConical },
]

const AppSidebar: React.FC = () => {
  const location = useLocation()

  return (
    <Sidebar>
      <SidebarHeader>
        <span className="font-heading px-2 py-1 text-sm font-semibold">Carbon Calculator</span>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Menu</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {NAV_ITEMS.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton asChild isActive={location.pathname === item.href}>
                    <Link to={item.href}>
                      <item.icon />
                      <span>{item.label}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter />
    </Sidebar>
  )
}

interface AppLayoutProps {
  children: ReactNode
  requireInstitution?: boolean
}

export const AppLayout: React.FC<AppLayoutProps> = ({ children, requireInstitution = true }) => {
  const { hasInstitution } = useInstitution()
  const showContent = !requireInstitution || resolveLayoutView(hasInstitution) === 'content'

  const { data: institutions = [] } = useQuery({
    queryKey: ['institutions'],
    queryFn: listInstitutions,
  })

  return (
    <TooltipProvider>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <header className="flex items-center gap-2 border-b px-4 py-3">
            <SidebarTrigger className="-ml-1" />
            <div className="ml-auto">
              <InstitutionSwitcher
                options={institutions.map((i) => ({ id: i.id, name: i.name }))}
              />
            </div>
          </header>
          <main className="flex-1 p-4">
            {showContent ? (
              children
            ) : (
              <p className="text-muted-foreground text-sm">
                Selecione uma instituição para continuar.
              </p>
            )}
          </main>
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}
