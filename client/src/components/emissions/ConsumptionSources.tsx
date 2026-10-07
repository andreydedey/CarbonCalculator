import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { EmissionResult } from '@/lib/api/emissions'
import { consumptionSourceLabel, isEstimate } from '@/lib/utils/consumption-source'

/** Where each configuration's consumption came from: measurement or manufacturer specification. */
export function ConsumptionSources({ sources }: { sources: EmissionResult['consumptionSources'] }) {
  if (sources.length === 0) return null
  const head = 'text-xs tracking-wider text-muted-foreground'

  return (
    <Card className="overflow-hidden">
      <div className="flex flex-col gap-1 border-b border-border px-6 py-5">
        <h2 className="text-base font-semibold">Origem do consumo</h2>
        <p className="text-[13px] text-muted-foreground">
          Potência usada por estação em cada configuração. Medições têm prioridade sobre a
          especificação do fabricante.
        </p>
      </div>
      <Table>
        <TableHeader>
          <TableRow className="bg-muted hover:bg-muted">
            <TableHead className={`px-6 ${head}`}>CONFIGURAÇÃO</TableHead>
            <TableHead className={`text-right ${head}`}>POTÊNCIA</TableHead>
            <TableHead className={`pr-6 ${head}`}>ORIGEM</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sources.map((s) => {
            const estimate = isEstimate(s.consumptionSource)
            return (
              <TableRow key={s.configurationId}>
                <TableCell className="px-6 text-[13px] font-medium">{s.label}</TableCell>
                <TableCell className="text-right font-mono text-sm font-semibold text-primary">
                  {s.totalWatts} W
                </TableCell>
                <TableCell className="pr-6">
                  <div className="flex items-center gap-2">
                    <Badge variant={estimate ? 'outline' : 'default'}>
                      {consumptionSourceLabel(s.consumptionSource)}
                    </Badge>
                    {estimate && <span className="text-xs text-muted-foreground">estimativa</span>}
                  </div>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </Card>
  )
}
