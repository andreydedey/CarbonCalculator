import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import type React from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { DatePicker } from '@/components/ui/date-picker'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { isApiError } from '@/lib/api/client'
import {
  type ConsumptionMeasurement,
  type CreateConsumptionMeasurementPayload,
  createConsumptionMeasurement,
  updateConsumptionMeasurement,
} from '@/lib/api/consumption-measurements'
import { listEquipmentModels } from '@/lib/api/equipment-models'
import { listMonitors } from '@/lib/api/monitors'
import { listOperatingSystems } from '@/lib/api/operating-systems'
import {
  type ConsumptionMeasurementFormValues,
  consumptionMeasurementFormSchema,
  TARGET_TYPE_OPTIONS,
} from '@/lib/schemas/consumptionMeasurementSchema'
import { toIsoDate } from '@/lib/utils/occupation'

interface ConsumptionMeasurementFormProps {
  measurement?: ConsumptionMeasurement
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved?: () => void
  defaultTargetType?: 'COMPUTER' | 'MONITOR' | 'COMBINED'
  defaultEquipmentModelId?: string
  defaultMonitorId?: string
}

export const ConsumptionMeasurementForm: React.FC<ConsumptionMeasurementFormProps> = ({
  measurement,
  open,
  onOpenChange,
  onSaved,
  defaultTargetType,
  defaultEquipmentModelId,
  defaultMonitorId,
}) => {
  const mode = measurement ? 'edit' : 'create'

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

  const { data: osPage } = useQuery({
    queryKey: ['operating-systems', 'all'],
    queryFn: () => listOperatingSystems({ size: 100 }),
    enabled: open,
  })
  const operatingSystems = osPage?.content ?? []

  const {
    handleSubmit,
    reset,
    register,
    control,
    formState: { errors, isDirty },
  } = useForm<ConsumptionMeasurementFormValues>({
    resolver: zodResolver(consumptionMeasurementFormSchema),
    values: measurement
      ? {
          targetType: measurement.targetType,
          equipmentModelId: measurement.equipmentModel?.id ?? '',
          operatingSystemId: measurement.operatingSystem?.id ?? '',
          monitorId: measurement.monitor?.id ?? '',
          averageWatts: measurement.averageWatts,
          durationMinutes: measurement.durationMinutes,
          readingIntervalMinutes: measurement.readingIntervalMinutes ?? '',
          measurementDate: measurement.measurementDate,
          conditions: measurement.conditions ?? '',
        }
      : {
          targetType: defaultTargetType ?? 'COMPUTER',
          equipmentModelId: defaultEquipmentModelId ?? '',
          operatingSystemId: '',
          monitorId: defaultMonitorId ?? '',
          averageWatts: 0,
          durationMinutes: 12,
          readingIntervalMinutes: 4,
          measurementDate: toIsoDate(new Date()),
          conditions: '',
        },
  })

  const targetType = useWatch({ control, name: 'targetType' })
  const showComputer = targetType === 'COMPUTER' || targetType === 'COMBINED'
  const showMonitor = targetType === 'MONITOR' || targetType === 'COMBINED'

  const saveMutation = useMutation({
    mutationFn: (payload: CreateConsumptionMeasurementPayload) =>
      mode === 'edit' && measurement
        ? updateConsumptionMeasurement(measurement.id, payload)
        : createConsumptionMeasurement(payload),
  })

  async function onSubmit(values: ConsumptionMeasurementFormValues) {
    const payload: CreateConsumptionMeasurementPayload = {
      targetType: values.targetType,
      equipmentModelId: showComputer ? values.equipmentModelId || null : null,
      operatingSystemId: showComputer ? values.operatingSystemId || null : null,
      monitorId: showMonitor ? values.monitorId || null : null,
      averageWatts: values.averageWatts,
      durationMinutes: values.durationMinutes,
      readingIntervalMinutes: values.readingIntervalMinutes
        ? Number(values.readingIntervalMinutes)
        : null,
      measurementDate: values.measurementDate,
      conditions: values.conditions || null,
    }

    try {
      const result = await saveMutation.mutateAsync(payload)
      reset()
      onOpenChange(false)
      onSaved?.()

      if (result.outlierWarning) {
        toast.warning(
          `Medição registrada, mas difere ${result.outlierWarning.deviationPercent.toFixed(0)}% da média existente (${result.outlierWarning.existingAverage.toFixed(1)} W). Verifique o protocolo de medição.`,
          { duration: 8000 },
        )
      } else {
        toast.success(mode === 'edit' ? 'Medição atualizada.' : 'Medição registrada.')
      }
    } catch (error) {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar a medição.')
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
      <DialogContent className="sm:max-w-[560px] gap-0 p-0 max-h-[90vh] overflow-y-auto">
        <DialogHeader className="px-7 pt-5 pb-4">
          <DialogTitle className="text-base font-semibold">
            {mode === 'edit' ? 'Editar Medição' : 'Registrar Medição'}
          </DialogTitle>
          <DialogDescription>
            Registre o consumo médio medido com wattímetro. Valores sugeridos de protocolo: duração
            12 min, intervalo entre leituras 4 min.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="flex flex-col gap-5 px-7 pb-6">
            <div className="flex gap-4">
              <div className="flex flex-1 flex-col gap-1.5">
                <Label>Tipo de alvo *</Label>
                <Controller
                  control={control}
                  name="targetType"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder="Selecionar..." />
                      </SelectTrigger>
                      <SelectContent>
                        {TARGET_TYPE_OPTIONS.map((opt) => (
                          <SelectItem key={opt.value} value={opt.value}>
                            {opt.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                <FieldError message={errors.targetType?.message} />
              </div>
              <div className="flex w-56 flex-col gap-1.5">
                <Label>Data da medição *</Label>
                <Controller
                  control={control}
                  name="measurementDate"
                  render={({ field }) => (
                    <DatePicker
                      value={field.value}
                      onChange={field.onChange}
                      maxDate={toIsoDate(new Date())}
                    />
                  )}
                />
                <FieldError message={errors.measurementDate?.message} />
              </div>
            </div>

            <div className="flex flex-col gap-4">
              {showComputer && (
                <div className="flex gap-4">
                  <div className="flex flex-1 flex-col gap-1.5">
                    <Label>Computador *</Label>
                    <Controller
                      control={control}
                      name="equipmentModelId"
                      render={({ field }) => (
                        <Select value={field.value} onValueChange={field.onChange}>
                          <SelectTrigger className="w-full">
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
                      )}
                    />
                    <FieldError message={errors.equipmentModelId?.message} />
                  </div>
                  <div className="flex w-40 flex-col gap-1.5">
                    <Label>Sistema operacional *</Label>
                    <Controller
                      control={control}
                      name="operatingSystemId"
                      render={({ field }) => (
                        <Select value={field.value} onValueChange={field.onChange}>
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder="Selecionar..." />
                          </SelectTrigger>
                          <SelectContent>
                            {operatingSystems.map((os) => (
                              <SelectItem key={os.id} value={os.id}>
                                {os.name}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                    />
                    <FieldError message={errors.operatingSystemId?.message} />
                  </div>
                </div>
              )}

              {showMonitor && (
                <div className="flex flex-1 flex-col gap-1.5">
                  <Label>Monitor *</Label>
                  <Controller
                    control={control}
                    name="monitorId"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Selecionar..." />
                        </SelectTrigger>
                        <SelectContent>
                          {monitors.map((mon) => (
                            <SelectItem key={mon.id} value={mon.id}>
                              {mon.name}
                              {mon.watts ? ` (${mon.watts}W)` : ''}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                  <FieldError message={errors.monitorId?.message} />
                </div>
              )}

              {targetType === 'COMBINED' && (
                <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
                  <p className="text-xs text-amber-800">
                    A medição conjunta captura computador e monitor na mesma tomada. Use quando o
                    monitor está conectado e influencia o consumo da GPU.
                  </p>
                </div>
              )}
            </div>

            <div className="flex gap-4">
              <div className="flex flex-1 flex-col gap-1.5">
                <Label>Consumo médio (W) *</Label>
                <Input type="number" step="0.01" min="0.01" {...register('averageWatts')} />
                <FieldError message={errors.averageWatts?.message} />
              </div>
              <div className="flex flex-1 flex-col gap-1.5">
                <Label>Duração (min) *</Label>
                <Input type="number" min="1" {...register('durationMinutes')} />
                <FieldError message={errors.durationMinutes?.message} />
              </div>
              <div className="flex flex-1 flex-col gap-1.5">
                <Label>Intervalo (min)</Label>
                <Input
                  type="number"
                  min="1"
                  title="Intervalo entre leituras do wattímetro"
                  {...register('readingIntervalMinutes')}
                />
                <FieldError message={errors.readingIntervalMinutes?.message} />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label>Condições (o que estava rodando e conectado)</Label>
              <Textarea
                rows={2}
                placeholder="Ex.: Navegador com 5 abas, editor de texto, monitor ligado via HDMI"
                {...register('conditions')}
              />
            </div>
          </div>

          <DialogFooter className="mx-0 mb-0">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={!isDirty || saveMutation.isPending}>
              {mode === 'edit' ? 'Salvar alterações' : 'Registrar Medição'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
