import { ChevronDown, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import type { EmissionResult } from '@/lib/api/emissions'

interface TransparencyPanelProps {
  inputs: EmissionResult['inputs']
}

export function TransparencyPanel({ inputs }: TransparencyPanelProps) {
  const [open, setOpen] = useState(false)

  return (
    <div className="rounded-lg border">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-4 py-3 text-sm font-medium hover:bg-muted/50 transition-colors"
        onClick={() => setOpen(!open)}
      >
        {open ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
        Transparência — dados de entrada usados no cálculo
      </button>

      {open && (
        <div className="border-t px-4 py-3 space-y-4">
          <div>
            <h4 className="text-xs font-semibold mb-2">Fatores de emissão aplicados</h4>
            <div className="rounded border overflow-hidden">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-muted/30">
                    <th className="px-3 py-1.5 text-left font-medium">Mês</th>
                    <th className="px-3 py-1.5 text-right font-medium">Valor (kgCO₂/kWh)</th>
                    <th className="px-3 py-1.5 text-left font-medium">Fonte</th>
                  </tr>
                </thead>
                <tbody>
                  {inputs.emissionFactors.map((f) => (
                    <tr key={f.month} className="border-b last:border-b-0">
                      <td className="px-3 py-1.5 font-mono">{f.month}</td>
                      <td className="px-3 py-1.5 text-right font-mono">{f.value}</td>
                      <td className="px-3 py-1.5 text-muted-foreground">{f.source}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold mb-2">Fontes de consumo por configuração</h4>
            <div className="rounded border overflow-hidden">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b bg-muted/30">
                    <th className="px-3 py-1.5 text-left font-medium">Configuração</th>
                    <th className="px-3 py-1.5 text-left font-medium">Origem</th>
                    <th className="px-3 py-1.5 text-right font-medium">Computador (W)</th>
                    <th className="px-3 py-1.5 text-right font-medium">Monitor (W)</th>
                    <th className="px-3 py-1.5 text-right font-medium">Total (W)</th>
                  </tr>
                </thead>
                <tbody>
                  {inputs.consumptionSources.map((c) => (
                    <tr key={c.configurationId} className="border-b last:border-b-0">
                      <td className="px-3 py-1.5">{c.label}</td>
                      <td className="px-3 py-1.5 text-muted-foreground">{c.source}</td>
                      <td className="px-3 py-1.5 text-right font-mono">{c.computerWatts}</td>
                      <td className="px-3 py-1.5 text-right font-mono">{c.monitorWatts}</td>
                      <td className="px-3 py-1.5 text-right font-mono font-medium">
                        {c.totalWatts}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
