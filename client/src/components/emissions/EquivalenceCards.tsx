import { Car, TreeDeciduous } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface EquivalenceCardsProps {
  carKm: number
  treesNeeded: number
}

export function EquivalenceCards({ carKm, treesNeeded }: EquivalenceCardsProps) {
  return (
    <div className="grid grid-cols-2 gap-4">
      <Card size="sm">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Car className="size-4 text-muted-foreground" />
            Equivalência em km de carro
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-2xl font-bold">{carKm.toLocaleString('pt-BR')} km</p>
          <p className="text-xs text-muted-foreground mt-1">
            Distância equivalente percorrida por um carro comum
          </p>
        </CardContent>
      </Card>
      <Card size="sm">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <TreeDeciduous className="size-4 text-muted-foreground" />
            Árvores para absorver
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-2xl font-bold">{treesNeeded.toFixed(1)}</p>
          <p className="text-xs text-muted-foreground mt-1">
            Árvores urbanas necessárias por 1 ano para absorver essa emissão
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
