import type { LaboratoryEmission } from '@/lib/api/emissions'

interface EmissionsByLabProps {
  data: LaboratoryEmission[]
  totalEmission: number
}

export function EmissionsByLab({ data, totalEmission }: EmissionsByLabProps) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-sm font-semibold">Emissão por laboratório</h3>
      <div className="rounded-lg border overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-muted/50">
              <th className="px-4 py-2 text-left font-medium">Laboratório</th>
              <th className="px-4 py-2 text-right font-medium">Estações</th>
              <th className="px-4 py-2 text-right font-medium">Energia (kWh)</th>
              <th className="px-4 py-2 text-right font-medium">Emissão (kgCO₂)</th>
              <th className="px-4 py-2 text-right font-medium">Computador</th>
              <th className="px-4 py-2 text-right font-medium">Monitor</th>
              <th className="px-4 py-2 text-right font-medium">%</th>
            </tr>
          </thead>
          <tbody>
            {data.map((lab) => {
              const pct = totalEmission > 0 ? (lab.emissionKg / totalEmission) * 100 : 0
              return (
                <tr key={lab.laboratoryId} className="border-b last:border-b-0">
                  <td className="px-4 py-2 font-medium">{lab.laboratoryName}</td>
                  <td className="px-4 py-2 text-right font-mono text-xs">{lab.stationCount}</td>
                  <td className="px-4 py-2 text-right font-mono text-xs">
                    {lab.energyKwh.toFixed(1)}
                  </td>
                  <td className="px-4 py-2 text-right font-mono text-xs font-medium">
                    {lab.emissionKg.toFixed(2)}
                  </td>
                  <td className="px-4 py-2 text-right font-mono text-xs text-muted-foreground">
                    {lab.computerEmissionKg.toFixed(2)}
                  </td>
                  <td className="px-4 py-2 text-right font-mono text-xs text-muted-foreground">
                    {lab.monitorEmissionKg.toFixed(2)}
                  </td>
                  <td className="px-4 py-2 text-right font-mono text-xs">{pct.toFixed(1)}%</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
