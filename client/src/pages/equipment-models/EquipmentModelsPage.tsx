import { Plus } from 'lucide-react'
import { useState } from 'react'
import { ConfigurationList } from '@/components/configurations/ConfigurationList'
import { EquipmentModelList } from '@/components/equipment-models/EquipmentModelList'
import { MonitorList } from '@/components/monitors/MonitorList'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

const TAB_LABELS = {
  computadores: 'Novo Computador',
  monitores: 'Novo Monitor',
  configuracoes: 'Nova Configuração',
} as const

type TabValue = keyof typeof TAB_LABELS

export function EquipmentModelsPage() {
  const [tab, setTab] = useState<TabValue>('computadores')
  const [computerFormOpen, setComputerFormOpen] = useState(false)
  const [monitorFormOpen, setMonitorFormOpen] = useState(false)
  const [configFormOpen, setConfigFormOpen] = useState(false)

  function handleAdd() {
    if (tab === 'computadores') setComputerFormOpen(true)
    else if (tab === 'monitores') setMonitorFormOpen(true)
    else setConfigFormOpen(true)
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-normal text-muted-foreground">
            Cadastro &rsaquo; Equipamentos
          </p>
          <h1 className="font-heading text-2xl font-bold">Equipamentos</h1>
        </div>
        <Button onClick={handleAdd}>
          <Plus className="size-4" />
          {TAB_LABELS[tab]}
        </Button>
      </div>

      <Tabs value={tab} onValueChange={(v) => setTab(v as TabValue)}>
        <TabsList variant="line">
          <TabsTrigger value="computadores">Computadores</TabsTrigger>
          <TabsTrigger value="monitores">Monitores</TabsTrigger>
          <TabsTrigger value="configuracoes">Configurações</TabsTrigger>
        </TabsList>
        <TabsContent value="computadores">
          <EquipmentModelList formOpen={computerFormOpen} onFormOpenChange={setComputerFormOpen} />
        </TabsContent>
        <TabsContent value="monitores">
          <MonitorList formOpen={monitorFormOpen} onFormOpenChange={setMonitorFormOpen} />
        </TabsContent>
        <TabsContent value="configuracoes">
          <ConfigurationList formOpen={configFormOpen} onFormOpenChange={setConfigFormOpen} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
