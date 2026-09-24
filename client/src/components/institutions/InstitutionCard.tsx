import { Check, Cpu, LogIn, Monitor, Pencil } from 'lucide-react'
import type React from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { Institution } from '@/lib/api/institutions'

interface InstitutionCardProps {
  institution: Institution
  isCurrent?: boolean
  onEnter: (institution: Institution) => void
  onEdit?: (institution: Institution) => void
}

function initials(acronym: string): string {
  return acronym.slice(0, 2).toUpperCase()
}

function locationLabel(city: string | null, state: string): string {
  return city ? `${city}, ${state}` : state
}

export const InstitutionCard: React.FC<InstitutionCardProps> = ({
  institution,
  isCurrent = false,
  onEnter,
  onEdit,
}) => (
  <div className="flex flex-col overflow-hidden rounded-lg border bg-card">
    <div className="flex items-center gap-4 p-5">
      <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
        {initials(institution.acronym)}
      </div>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-sm font-semibold">
          {institution.acronym} — {institution.name}
        </span>
        <span className="text-xs text-muted-foreground">
          {locationLabel(institution.city, institution.state)}
        </span>
      </div>
      <Badge variant={institution.active ? 'default' : 'secondary'} className="shrink-0">
        {institution.active ? 'Ativa' : 'Inativa'}
      </Badge>
    </div>

    <div className="border-t" />

    <div className="flex items-center gap-5 px-5 py-3">
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Cpu className="size-3.5" />
        <span>{institution.laboratoryCount} laboratórios</span>
      </div>
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Monitor className="size-3.5" />
        <span>{institution.equipmentCount} equipamentos</span>
      </div>
    </div>

    <div className="border-t" />

    <div className="flex items-center justify-end gap-2 px-5 py-3">
      {onEdit && (
        <Button variant="outline" size="sm" onClick={() => onEdit(institution)}>
          <Pencil className="size-3.5" />
          Editar
        </Button>
      )}
      {isCurrent ? (
        <Button size="sm" variant="secondary" disabled>
          <Check className="size-3.5" />
          Atual
        </Button>
      ) : (
        <Button size="sm" onClick={() => onEnter(institution)}>
          <LogIn className="size-3.5" />
          Entrar
        </Button>
      )}
    </div>
  </div>
)
