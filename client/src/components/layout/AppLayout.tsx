import { Building2, Calendar, Cpu, FlaskConical, LogOut, Users } from 'lucide-react'
import type React from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { Button } from '@/components/ui/button'
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
import { useAuth } from '@/context/AuthContext'

type NavItem = {
  label: string
  href: string
  icon: React.ComponentType
  requiresRole?: string
  adminOnly?: boolean
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Instituições', href: '/institutions', icon: Building2 },
  { label: 'Laboratórios', href: '/laboratories', icon: FlaskConical },
  { label: 'Equipamentos', href: '/equipment-models', icon: Cpu },
  { label: 'Calendário', href: '/academic-periods', icon: Calendar },
  { label: 'Usuários', href: '/users', icon: Users, requiresRole: 'MANAGER' },
]

const AppSidebar: React.FC = () => {
  const location = useLocation()
  const { user } = useAuth()

  const visibleItems = NAV_ITEMS.filter((item) => {
    if (item.adminOnly && !user?.admin) return false
    if (item.requiresRole) {
      const membership = user?.institutions?.find((m) => m.status === 'ACTIVE')
      if (!membership && !user?.admin) return false
      if (item.requiresRole === 'MANAGER' && !user?.admin && membership?.role !== 'MANAGER') {
        return false
      }
    }
    return true
  })

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
              {visibleItems.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton asChild isActive={location.pathname.startsWith(item.href)}>
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

export const AppLayout: React.FC = () => {
  const { user, logout } = useAuth()

  return (
    <TooltipProvider>
      <SidebarProvider>
        <AppSidebar />
        <SidebarInset>
          <header className="flex items-center gap-2 border-b px-4 py-3">
            <SidebarTrigger className="-ml-1" />
            <div className="ml-auto flex items-center gap-3">
              {user && (
                <div className="flex items-center gap-2 border-l pl-3">
                  <span className="text-sm text-muted-foreground">{user.name}</span>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-7 w-7"
                    onClick={logout}
                    title="Sair"
                  >
                    <LogOut className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </div>
          </header>
          <main className="flex-1 p-4">
            <Outlet />
          </main>
        </SidebarInset>
      </SidebarProvider>
    </TooltipProvider>
  )
}
