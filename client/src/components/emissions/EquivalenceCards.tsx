import { Car, Lightbulb, TreePine } from 'lucide-react'
import type { ReactNode } from 'react'

interface EquivalenceCardsProps {
  carKm: number
  treesNeeded: number
  totalEnergyKwh: number
}

function EquivalenceCard({
  icon,
  value,
  description,
  subtitle,
}: {
  icon: ReactNode
  value: string
  description: string
  subtitle: string
}) {
  return (
    <div className="flex-1 rounded-lg border border-border bg-card p-4 flex items-center gap-3.5">
      <div className="flex size-11 shrink-0 items-center justify-center rounded-[10px] bg-accent">
        {icon}
      </div>
      <div className="flex flex-col gap-0.5">
        <span className="text-lg font-bold">{value}</span>
        <span className="text-xs text-muted-foreground">{description}</span>
        <span className="text-[11px] text-muted-foreground italic">{subtitle}</span>
      </div>
    </div>
  )
}

function formatNumber(n: number): string {
  return Math.round(n).toLocaleString('pt-BR')
}

export function EquivalenceCards({ carKm, treesNeeded, totalEnergyKwh }: EquivalenceCardsProps) {
  const ledHours = Math.round(totalEnergyKwh / 0.06)

  return (
    <div className="flex gap-4">
      <EquivalenceCard
        icon={<Car className="size-[22px] text-primary" />}
        value={`${formatNumber(carKm)} km`}
        description="de carro a gasolina"
        subtitle="distância equivalente em emissões"
      />
      <EquivalenceCard
        icon={<TreePine className="size-[22px] text-primary" />}
        value={`${Math.ceil(treesNeeded)} árvores`}
        description="para compensar em 1 ano"
        subtitle="plantio necessário para neutralizar"
      />
      <EquivalenceCard
        icon={<Lightbulb className="size-[22px] text-primary" />}
        value={`${formatNumber(ledHours)} h`}
        description="de lâmpada LED 60W"
        subtitle="equivalente em consumo energético"
      />
    </div>
  )
}
