import { useMutation } from '@tanstack/react-query'
import { Moon, Sun, Sunset } from 'lucide-react'
import type React from 'react'
import { useEffect, useState } from 'react'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { isApiError } from '@/lib/api/client'
import {
  type Shift,
  type ShiftInput,
  type ShiftType,
  replaceShifts,
} from '@/lib/api/academic-periods'

interface ShiftConfigModalProps {
  periodId: string
  currentShifts: Shift[]
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => void
}

const SHIFT_ORDER: ShiftType[] = ['MORNING', 'AFTERNOON', 'EVENING']

const SHIFT_META = {
  MORNING: { label: 'Manhã', icon: Sun, color: 'text-amber-500', defaultStart: '07:30' },
  AFTERNOON: { label: 'Tarde', icon: Sunset, color: 'text-orange-500', defaultStart: '13:30' },
  EVENING: { label: 'Noite', icon: Moon, color: 'text-indigo-500', defaultStart: '18:50' },
} as const

const DAY_OPTIONS = [
  { value: 1, label: 'Seg' },
  { value: 2, label: 'Ter' },
  { value: 3, label: 'Qua' },
  { value: 4, label: 'Qui' },
  { value: 5, label: 'Sex' },
  { value: 6, label: 'Sáb' },
]

type ShiftFormState = {
  enabled: boolean
  startTime: string
  classesPerDay: number
  classDurationMinutes: number
  breakDurationMinutes: number
  activeDays: number[]
}

function defaultShift(type: ShiftType): ShiftFormState {
  return {
    enabled: type !== 'EVENING',
    startTime: SHIFT_META[type].defaultStart,
    classesPerDay: type === 'EVENING' ? 4 : 5,
    classDurationMinutes: 50,
    breakDurationMinutes: 10,
    activeDays: [1, 2, 3, 4, 5],
  }
}

function fromExisting(shift: Shift): ShiftFormState {
  return {
    enabled: shift.enabled,
    startTime: shift.startTime.substring(0, 5),
    classesPerDay: shift.classesPerDay,
    classDurationMinutes: shift.classDurationMinutes,
    breakDurationMinutes: shift.breakDurationMinutes,
    activeDays: [...shift.activeDays],
  }
}

function calculateEndTime(s: ShiftFormState): string {
  const [h, m] = s.startTime.split(':').map(Number)
  const totalMinutes = (s.classesPerDay * s.classDurationMinutes) + ((s.classesPerDay - 1) * s.breakDurationMinutes)
  const endH = h + Math.floor((m + totalMinutes) / 60)
  const endM = (m + totalMinutes) % 60
  return `${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}`
}

export const ShiftConfigModal: React.FC<ShiftConfigModalProps> = ({
  periodId,
  currentShifts,
  open,
  onOpenChange,
  onSaved,
}) => {
  const [shifts, setShifts] = useState<Record<ShiftType, ShiftFormState>>(() => {
    const state = {} as Record<ShiftType, ShiftFormState>
    for (const type of SHIFT_ORDER) {
      const existing = currentShifts.find((s) => s.shiftType === type)
      state[type] = existing ? fromExisting(existing) : defaultShift(type)
    }
    return state
  })

  useEffect(() => {
    if (open) {
      const state = {} as Record<ShiftType, ShiftFormState>
      for (const type of SHIFT_ORDER) {
        const existing = currentShifts.find((s) => s.shiftType === type)
        state[type] = existing ? fromExisting(existing) : defaultShift(type)
      }
      setShifts(state)
    }
  }, [open, currentShifts])

  const saveMutation = useMutation({
    mutationFn: (inputs: ShiftInput[]) => replaceShifts(periodId, inputs),
    onSuccess: () => {
      toast.success('Turnos salvos.')
      onSaved()
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar os turnos.')
    },
  })

  function updateShift(type: ShiftType, patch: Partial<ShiftFormState>) {
    setShifts((prev) => ({ ...prev, [type]: { ...prev[type], ...patch } }))
  }

  function toggleDay(type: ShiftType, day: number) {
    setShifts((prev) => {
      const current = prev[type].activeDays
      const next = current.includes(day) ? current.filter((d) => d !== day) : [...current, day].sort()
      return { ...prev, [type]: { ...prev[type], activeDays: next } }
    })
  }

  function handleSave() {
    const inputs: ShiftInput[] = SHIFT_ORDER
      .filter((type) => shifts[type].enabled || currentShifts.some((s) => s.shiftType === type))
      .map((type) => {
        const s = shifts[type]
        return {
          shiftType: type,
          startTime: s.startTime,
          classesPerDay: s.classesPerDay,
          classDurationMinutes: s.classDurationMinutes,
          breakDurationMinutes: s.breakDurationMinutes,
          activeDays: s.activeDays,
          enabled: s.enabled,
        }
      })
    saveMutation.mutate(inputs)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Configurar Turnos</DialogTitle>
          <DialogDescription>
            Defina os turnos de aula do período. O horário de término é calculado automaticamente.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-6">
          {SHIFT_ORDER.map((type) => {
            const meta = SHIFT_META[type]
            const Icon = meta.icon
            const s = shifts[type]
            const endTime = calculateEndTime(s)

            return (
              <div key={type} className="rounded-lg border p-4">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-2">
                    <Icon className={`size-5 ${meta.color}`} />
                    <span className="font-semibold">{meta.label}</span>
                  </div>
                  <Switch
                    checked={s.enabled}
                    onCheckedChange={(enabled) => updateShift(type, { enabled })}
                  />
                </div>

                {s.enabled && (
                  <div className="flex flex-col gap-4">
                    <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                      <div className="flex flex-col gap-1.5">
                        <Label className="text-xs">Horário Início</Label>
                        <Input
                          type="time"
                          value={s.startTime}
                          onChange={(e) => updateShift(type, { startTime: e.target.value })}
                        />
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <Label className="text-xs">Fim (calculado)</Label>
                        <Input type="time" value={endTime} disabled />
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <Label className="text-xs">Aulas/Dia</Label>
                        <Input
                          type="number"
                          min={1}
                          max={10}
                          value={s.classesPerDay}
                          onChange={(e) => updateShift(type, { classesPerDay: Number(e.target.value) })}
                        />
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <Label className="text-xs">Duração (min)</Label>
                        <Input
                          type="number"
                          min={1}
                          value={s.classDurationMinutes}
                          onChange={(e) => updateShift(type, { classDurationMinutes: Number(e.target.value) })}
                        />
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div className="flex flex-col gap-1.5">
                        <Label className="text-xs">Intervalo (min)</Label>
                        <Input
                          type="number"
                          min={0}
                          value={s.breakDurationMinutes}
                          onChange={(e) => updateShift(type, { breakDurationMinutes: Number(e.target.value) })}
                        />
                      </div>
                      <div className="flex flex-col gap-1.5">
                        <Label className="text-xs">Dias Ativos</Label>
                        <div className="flex gap-1 flex-wrap">
                          {DAY_OPTIONS.map((day) => (
                            <button
                              key={day.value}
                              type="button"
                              className={`rounded-md border px-2 py-1 text-xs font-medium transition-colors ${
                                s.activeDays.includes(day.value)
                                  ? 'bg-primary text-primary-foreground border-primary'
                                  : 'bg-background hover:bg-accent'
                              }`}
                              onClick={() => toggleDay(type, day.value)}
                            >
                              {day.label}
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>

                    <div className="rounded bg-muted/50 px-3 py-2 text-xs text-muted-foreground">
                      {s.classesPerDay} aulas × {s.classDurationMinutes}min + {s.classesPerDay - 1} intervalos × {s.breakDurationMinutes}min = {s.classesPerDay * s.classDurationMinutes + (s.classesPerDay - 1) * s.breakDurationMinutes}min ({s.startTime} → {endTime})
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button disabled={saveMutation.isPending} onClick={handleSave}>
            Salvar Turnos
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
