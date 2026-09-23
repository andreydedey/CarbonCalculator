import { Plus } from 'lucide-react'
import { useState } from 'react'
import { EquipmentModelList } from '@/components/equipment-models/EquipmentModelList'
import { MonitorList } from '@/components/monitors/MonitorList'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

const TAB_LABELS = {
  computadores: 'Novo Computador',
  monitores: 'Novo Monitor',
} as const

type TabValue = keyof typeof TAB_LABELS

export function EquipmentModelsPage() {
  const [tab, setTab] = useState<TabValue>('computadores')
  const [computerFormOpen, setComputerFormOpen] = useState(false)
  const [monitorFormOpen, setMonitorFormOpen] = useState(false)

  function handleAdd() {
    if (tab === 'computadores') setComputerFormOpen(true)
    else setMonitorFormOpen(true)
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
        </TabsList>
        <TabsContent value="computadores">
          <EquipmentModelList formOpen={computerFormOpen} onFormOpenChange={setComputerFormOpen} />
        </TabsContent>
        <TabsContent value="monitores">
          <MonitorList formOpen={monitorFormOpen} onFormOpenChange={setMonitorFormOpen} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
