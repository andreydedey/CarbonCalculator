import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, Monitor } from 'lucide-react'
import type React from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FieldError } from '@/components/ui/field-error'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { isApiError } from '@/lib/api/client'
import {
  type Configuration,
  type CreateConfigurationPayload,
  createConfiguration,
  updateConfiguration,
} from '@/lib/api/configurations'
import { listEquipmentModels } from '@/lib/api/equipment-models'
import { listMonitors } from '@/lib/api/monitors'
import {
  type ConfigurationFormValues,
  configurationFormSchema,
  OS_OPTIONS,
} from '@/lib/schemas/configurationSchema'

interface ConfigurationFormProps {
  configuration?: Configuration
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
}

export const ConfigurationForm: React.FC<ConfigurationFormProps> = ({
  configuration,
  open,
  onOpenChange,
  onSaved,
}) => {
  const mode = configuration ? 'edit' : 'create'

  const { data: modelsPage } = useQuery({
    queryKey: ['equipment-models', 'all'],
    queryFn: () => listEquipmentModels({ size: 100 }),
    enabled: open,
  })
  const models = modelsPage?.content ?? []

  const { data: monitorsPage } = useQuery({
    queryKey: ['monitors', 'all'],
    queryFn: () => listMonitors({ size: 100 }),
    enabled: open,
  })
  const monitors = monitorsPage?.content ?? []

  const {
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<ConfigurationFormValues>({
    resolver: zodResolver(configurationFormSchema),
    values: {
      equipmentModelId: configuration?.equipmentModel.id ?? '',
      operatingSystem: configuration?.operatingSystem ?? '',
      monitorId: configuration?.monitor?.id ?? '',
    },
  })

  const selectedModel = models.find((m) => m.id === watch('equipmentModelId'))
  const hasIntegratedScreen = selectedModel?.hasIntegratedScreen ?? false
  const showMonitorWarning = !!selectedModel && !hasIntegratedScreen && !watch('monitorId')

  const saveMutation = useMutation({
    mutationFn: (payload: CreateConfigurationPayload) =>
      mode === 'edit' && configuration
        ? updateConfiguration(configuration.id, payload)
        : createConfiguration(payload),
  })

  async function onSubmit(values: ConfigurationFormValues) {
    const finalMonitorId = hasIntegratedScreen ? undefined : values.monitorId || undefined
    try {
      await saveMutation.mutateAsync({
        equipmentModelId: values.equipmentModelId,
        operatingSystem: values.operatingSystem.trim(),
        monitorId: finalMonitorId ?? null,
      })
      reset()
      onOpenChange(false)
      onSaved?.()
      toast.success(mode === 'edit' ? 'Configuração atualizada.' : 'Configuração cadastrada.')
    } catch (error) {
      toast.error(
        isApiError(error) ? error.message : 'Não foi possível salvar a configuração.',
      )
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      reset()
      saveMutation.reset()
    }
    onOpenChange(nextOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-[500px] gap-0 p-0">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Configuração' : 'Nova Configuração'}
          </DialogTitle>
          <DialogDescription>
            Uma combinação de computador, sistema operacional e monitor — reutilizável em qualquer
            laboratório.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="flex gap-4">
              <div className="flex flex-1 flex-col gap-1.5">
                <Label>Computador *</Label>
                <Select
                  value={watch('equipmentModelId')}
                  onValueChange={(v) => setValue('equipmentModelId', v)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecionar..." />
                  </SelectTrigger>
                  <SelectContent>
                    {models.map((m) => (
                      <SelectItem key={m.id} value={m.id}>
                        {m.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldError message={errors.equipmentModelId?.message} />
              </div>

              <div className="flex flex-1 flex-col gap-1.5">
                <Label>Sistema Operacional *</Label>
                <Select
                  value={watch('operatingSystem')}
                  onValueChange={(v) => setValue('operatingSystem', v)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecionar..." />
                  </SelectTrigger>
                  <SelectContent>
                    {OS_OPTIONS.map((os) => (
                      <SelectItem key={os} value={os}>
                        {os}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldError message={errors.operatingSystem?.message} />
              </div>
            </div>

            {hasIntegratedScreen ? (
              <div className="flex items-center gap-2 rounded-md border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-800">
                <Monitor className="size-3.5 shrink-0" />
                <span>Este computador possui tela integrada — monitor externo não aplicável.</span>
              </div>
            ) : (
              <div className="flex flex-col gap-1.5">
                <Label>Monitor</Label>
                <Select
                  value={watch('monitorId') || ''}
                  onValueChange={(v) => setValue('monitorId', v === '__none__' ? '' : v)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Selecionar..." />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none__">Nenhum</SelectItem>
                    {monitors.map((mon) => (
                      <SelectItem key={mon.id} value={mon.id}>
                        {mon.name}
                        {mon.watts ? ` (${mon.watts}W)` : ''}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FieldError message={errors.monitorId?.message} />
              </div>
            )}

            {showMonitorWarning && (
              <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3">
                <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
                <div className="flex flex-col gap-1 text-xs text-amber-800">
                  <span className="font-medium">
                    Sem monitor, o resultado fica subestimado
                  </span>
                  <span>
                    Este computador não tem tela integrada. Monitores representaram 69% e 40% dos
                    dispositivos nos casos analisados por Sutton-Parker e ficaram fora da
                    contabilidade.
                  </span>
                </div>
              </div>
            )}
          </div>

          <DialogFooter className="mx-0 mb-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saveMutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Cadastrar Configuração'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
