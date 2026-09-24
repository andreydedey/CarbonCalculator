import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, Monitor, MonitorOff, Pencil, Trash2 } from 'lucide-react'
import type React from 'react'
import { toast } from 'sonner'
import { LinkEquipmentDialog } from '@/components/laboratories/LinkEquipmentDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useDialog } from '@/hooks/use-dialog'
import { ApiError } from '@/lib/api/client'
import {
  getLabComposition,
  type LaboratoryEquipment,
  unlinkEquipment,
} from '@/lib/api/laboratory-equipment'

interface LaboratoryEquipmentSectionProps {
  labId: string
}

export const LaboratoryEquipmentSection: React.FC<LaboratoryEquipmentSectionProps> = ({
  labId,
}) => {
  const linkDialog = useDialog<LaboratoryEquipment>()

  const { data: composition, refetch } = useQuery({
    queryKey: ['lab-composition', labId],
    queryFn: () => getLabComposition(labId),
    enabled: !!labId,
  })

  const unlinkMutation = useMutation({
    mutationFn: (id: string) => unlinkEquipment(labId, id),
    onSuccess: () => {
      refetch()
      toast.success('Configuração removida do laboratório.')
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível desvincular.')
    },
  })

  function handleSaved() {
    linkDialog.closeDialog()
    refetch()
  }

  const items = composition?.items ?? []

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-semibold">Equipamentos do Laboratório</span>
        <Button type="button" variant="outline" size="sm" onClick={() => linkDialog.openDialog()}>
          Adicionar Configuração
        </Button>
      </div>

      <LinkEquipmentDialog
        labId={labId}
        equipment={linkDialog.data ?? undefined}
        open={linkDialog.open}
        onOpenChange={(open) => !open && linkDialog.closeDialog()}
        onSaved={handleSaved}
      />

      {items.length === 0 ? (
        <>
          <p className="text-xs text-muted-foreground">
            Adicione configurações cadastradas na instituição e informe a quantidade de estações em
            uso neste laboratório.
          </p>
          <div className="rounded-lg border">
            <div className="flex items-center justify-center px-4 py-4">
              <p className="text-xs text-muted-foreground italic">
                Use &ldquo;Adicionar Configuração&rdquo; para vincular configurações da instituição
              </p>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="rounded-lg border overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-4 py-2 text-left font-medium">Modelo</th>
                  <th className="px-4 py-2 text-left font-medium">CPU / TDP</th>
                  <th className="px-4 py-2 text-right font-medium">Núcleos</th>
                  <th className="px-4 py-2 text-right font-medium">RAM</th>
                  <th className="px-4 py-2 text-left font-medium">Monitor</th>
                  <th className="px-4 py-2 text-left font-medium">Sistema Op.</th>
                  <th className="px-4 py-2 text-right font-medium">Qtd.</th>
                  <th className="px-4 py-2 text-right font-medium">Ações</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const m = item.equipmentModel
                  const cpuTdp = [m.processor, m.tdpWatts ? `${m.tdpWatts}W` : null]
                    .filter(Boolean)
                    .join(' / ')
                  const hasMonitor = item.monitor != null || m.hasIntegratedScreen
                  const monitorLabel = m.hasIntegratedScreen
                    ? 'Tela integrada'
                    : item.monitor
                      ? [item.monitor.name, item.monitor.watts ? `${item.monitor.watts}W` : null]
                          .filter(Boolean)
                          .join(' / ')
                      : null
                  return (
                    <tr key={item.id} className="border-b last:border-b-0">
                      <td className="px-4 py-2 font-medium">{m.name}</td>
                      <td className="px-4 py-2 font-mono text-xs text-muted-foreground">
                        {cpuTdp || '-'}
                      </td>
                      <td className="px-4 py-2 text-right font-mono text-xs">
                        {m.coreCount ?? '-'}
                      </td>
                      <td className="px-4 py-2 text-right font-mono text-xs">
                        {m.memoryGb ? `${m.memoryGb} GB` : '-'}
                      </td>
                      <td className="px-4 py-2 text-xs">
                        {hasMonitor ? (
                          <span className="flex items-center gap-1 text-muted-foreground">
                            {m.hasIntegratedScreen && <Monitor className="size-3" />}
                            {monitorLabel}
                          </span>
                        ) : (
                          <Badge variant="destructive" className="gap-1">
                            <MonitorOff className="size-3" />
                            Sem monitor
                          </Badge>
                        )}
                      </td>
                      <td className="px-4 py-2">
                        {item.operatingSystem && (
                          <Badge variant="secondary">{item.operatingSystem}</Badge>
                        )}
                      </td>
                      <td className="px-4 py-2 text-right font-mono text-xs">{item.quantity}</td>
                      <td className="px-4 py-2 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="size-7"
                            onClick={() => linkDialog.openDialog(item)}
                          >
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="size-7 text-destructive"
                            onClick={() => unlinkMutation.mutate(item.id)}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              Total: <strong className="text-foreground">{composition?.totalMachines}</strong>{' '}
              máquinas
            </span>
            {composition && composition.configurationsWithoutMonitor > 0 && (
              <div className="flex items-center gap-1 text-amber-600">
                <AlertTriangle className="size-3.5" />
                <span>
                  {composition.configurationsWithoutMonitor} configuração(ões) sem monitor — consumo
                  subestimado
                </span>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
