import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BookOpen, CalendarDays, Clock, Layers, Percent, Save } from 'lucide-react'
import type React from 'react'
import { useCallback, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { type Occupancy, OccupationGrid } from '@/components/academic-periods/OccupationGrid'
import { OccurrencesView } from '@/components/academic-periods/OccurrencesView'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Progress } from '@/components/ui/progress'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useCanManage } from '@/hooks/useCanManage'
import {
  getAcademicPeriod,
  getSchedule,
  replaceSchedule,
  type ScheduleEntry,
  type ScheduleInput,
} from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'
import { listLaboratories } from '@/lib/api/laboratories'

type Mode = 'grid' | 'occurrences'

export function buildOccupancy(entries: ScheduleEntry[]): Occupancy {
  const map: Occupancy = {}
  for (const entry of entries) {
    if (!map[entry.shiftId]) map[entry.shiftId] = {}
    const slots: Record<number, number> = {}
    entry.occupiedSlots.forEach((slot, i) => {
      slots[slot] = entry.stationsUsed[i]
    })
    map[entry.shiftId][entry.dayOfWeek] = slots
  }
  return map
}

function occupancyToInputs(occupancy: Occupancy): ScheduleInput[] {
  const inputs: ScheduleInput[] = []
  for (const [shiftId, days] of Object.entries(occupancy)) {
    for (const [dayStr, slots] of Object.entries(days)) {
      const occupiedSlots = Object.keys(slots)
        .map(Number)
        .sort((a, b) => a - b)
      if (occupiedSlots.length > 0) {
        inputs.push({
          shiftId,
          dayOfWeek: Number(dayStr),
          occupiedSlots,
          stationsUsed: occupiedSlots.map((slot) => slots[slot]),
        })
      }
    }
  }
  return inputs
}

function sameOccupancy(a: Occupancy, b: Occupancy): boolean {
  const key = (o: Occupancy) => JSON.stringify(occupancyToInputs(o).sort((x, y) =>
    `${x.shiftId}${x.dayOfWeek}`.localeCompare(`${y.shiftId}${y.dayOfWeek}`)))
  return key(a) === key(b)
}

function ApplyToAllButton({
  capacity,
  onApply,
}: {
  capacity: number
  onApply: (stations: number) => void
}) {
  const [open, setOpen] = useState(false)
  const [value, setValue] = useState(String(capacity || 1))
  const parsed = Number(value)
  const valid = Number.isInteger(parsed) && parsed >= 1 && (capacity <= 0 || parsed <= capacity)

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="sm">
          <Layers className="size-4" />
          Aplicar a todas…
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-72">
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (!valid) return
            onApply(parsed)
            setOpen(false)
          }}
        >
          <p className="text-sm font-semibold">Estações em todas as aulas ocupadas</p>
          <div className="flex items-center gap-2">
            <Input
              autoFocus
              type="number"
              min={1}
              max={capacity > 0 ? capacity : undefined}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              className="font-mono"
            />
            {capacity > 0 && <span className="shrink-0 text-xs text-muted-foreground">de {capacity}</span>}
          </div>
          <Button type="submit" size="sm" disabled={!valid}>
            Aplicar
          </Button>
        </form>
      </PopoverContent>
    </Popover>
  )
}

export const OccupationEditorPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canManage = useCanManage()
  const [searchParams, setSearchParams] = useSearchParams()
  const mode: Mode = searchParams.get('mode') === 'occurrences' ? 'occurrences' : 'grid'

  const [selectedLabId, setSelectedLabId] = useState<string | null>(searchParams.get('lab'))
  const [occupancy, setOccupancy] = useState<Occupancy>({})
  const prevDataRef = useRef<string | null>(null)

  const { data: period, isLoading: periodLoading } = useQuery({
    queryKey: ['academic-period', id],
    queryFn: () => getAcademicPeriod(id!),
    enabled: !!id,
  })

  const { data: labsPage } = useQuery({
    queryKey: ['laboratories', 'all'],
    queryFn: () => listLaboratories({ status: 'active', size: 100 }),
  })

  const laboratories = labsPage?.content ?? []

  const resolvedLabId = useMemo(() => {
    if (selectedLabId && laboratories.some((l) => l.id === selectedLabId)) return selectedLabId
    return laboratories[0]?.id ?? null
  }, [laboratories, selectedLabId])

  const capacity = laboratories.find((l) => l.id === resolvedLabId)?.totalStations ?? 0

  const {
    data: scheduleEntries,
    isLoading: scheduleLoading,
    refetch: refetchSchedule,
  } = useQuery({
    queryKey: ['schedule', id, resolvedLabId],
    queryFn: () => getSchedule(id!, resolvedLabId!),
    enabled: !!id && !!resolvedLabId,
  })

  const serverOccupancy = useMemo(
    () => (scheduleEntries ? buildOccupancy(scheduleEntries) : {}),
    [scheduleEntries],
  )

  // Sync server data to local state on load/lab change (no useEffect)
  const dataKey = `${resolvedLabId}-${JSON.stringify(scheduleEntries ?? null)}`
  if (dataKey !== prevDataRef.current && scheduleEntries) {
    prevDataRef.current = dataKey
    setOccupancy(serverOccupancy)
  }

  const dirty = useMemo(() => !sameOccupancy(occupancy, serverOccupancy), [occupancy, serverOccupancy])

  const handleSetStations = useCallback(
    (shiftId: string, dayOfWeek: number, slot: number, stations: number | null) => {
      setOccupancy((prev) => {
        const shiftDays = { ...(prev[shiftId] ?? {}) }
        const daySlots = { ...(shiftDays[dayOfWeek] ?? {}) }
        if (stations == null) delete daySlots[slot]
        else daySlots[slot] = stations
        shiftDays[dayOfWeek] = daySlots
        return { ...prev, [shiftId]: shiftDays }
      })
    },
    [],
  )

  const handleApplyToAll = useCallback((stations: number) => {
    setOccupancy((prev) => {
      const next: Occupancy = {}
      for (const [shiftId, days] of Object.entries(prev)) {
        next[shiftId] = {}
        for (const [day, slots] of Object.entries(days)) {
          next[shiftId][Number(day)] = Object.fromEntries(
            Object.keys(slots).map((slot) => [Number(slot), stations]),
          )
        }
      }
      return next
    })
  }, [])

  const handleLabChange = useCallback(
    (labId: string) => {
      setSelectedLabId(labId)
      prevDataRef.current = null
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev)
        next.set('lab', labId)
        return next
      })
    },
    [setSearchParams],
  )

  const handleModeChange = useCallback(
    (next: Mode) => {
      setSearchParams((prev) => {
        const params = new URLSearchParams(prev)
        if (next === 'occurrences') params.set('mode', 'occurrences')
        else params.delete('mode')
        return params
      })
    },
    [setSearchParams],
  )

  const handleCopyFrom = useCallback(
    async (sourceLabId: string) => {
      if (!id) return
      try {
        const entries = await getSchedule(id, sourceLabId)
        // Keep the source grid but never exceed this laboratory's capacity
        const copied = buildOccupancy(entries)
        if (capacity > 0) {
          for (const days of Object.values(copied)) {
            for (const slots of Object.values(days)) {
              for (const slot of Object.keys(slots)) {
                slots[Number(slot)] = Math.min(slots[Number(slot)], capacity)
              }
            }
          }
        }
        setOccupancy(copied)
        toast.success('Ocupação copiada. Salve para confirmar.')
      } catch {
        toast.error('Não foi possível copiar a ocupação.')
      }
    },
    [id, capacity],
  )

  const saveMutation = useMutation({
    mutationFn: () => replaceSchedule(id!, resolvedLabId!, occupancyToInputs(occupancy)),
    onSuccess: () => {
      refetchSchedule()
      queryClient.invalidateQueries({ queryKey: ['period-summary', id] })
      queryClient.invalidateQueries({ queryKey: ['emissions'] })
      toast.success('Ocupação salva.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar a ocupação.')
    },
  })

  const enabledShifts = useMemo(
    () => (period?.shifts ?? []).filter((s) => s.enabled),
    [period?.shifts],
  )

  const stats = useMemo(() => {
    let classes = 0
    let possible = 0
    let stationMinutes = 0
    let capacityMinutes = 0

    for (const shift of enabledShifts) {
      possible += shift.classesPerDay * shift.activeDays.length
      const shiftOcc = occupancy[shift.id] ?? {}
      for (const day of shift.activeDays) {
        for (const stations of Object.values(shiftOcc[day] ?? {})) {
          classes += 1
          stationMinutes += stations * shift.classDurationMinutes
          capacityMinutes += Math.max(capacity, stations) * shift.classDurationMinutes
        }
      }
    }

    return {
      classesPerWeek: classes,
      classesPossible: possible,
      stationHoursPerWeek: stationMinutes / 60,
      averageUsage: capacityMinutes > 0 ? (stationMinutes / capacityMinutes) * 100 : 0,
    }
  }, [enabledShifts, occupancy, capacity])

  if (periodLoading) {
    return <p className="text-sm text-muted-foreground">Carregando...</p>
  }

  if (!period) {
    return <p className="text-sm text-muted-foreground">Período não encontrado.</p>
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate(`/academic-periods/${id}`)}>
          <ArrowLeft className="size-4" />
        </Button>
        <div className="flex flex-col gap-0.5">
          <p className="text-xs font-normal text-muted-foreground">
            Cadastro &rsaquo; Calendário Letivo &rsaquo; {period.name} &rsaquo; Ocupação
          </p>
          <h1 className="font-heading text-2xl font-bold">Ocupação dos Laboratórios</h1>
        </div>
        {mode === 'grid' && canManage && (
          <div className="ml-auto">
            <Button disabled={!dirty || saveMutation.isPending} onClick={() => saveMutation.mutate()}>
              <Save className="size-4" />
              Salvar Ocupação
            </Button>
          </div>
        )}
      </div>

      <p className="rounded-md bg-accent px-4 py-2.5 text-[13px] text-accent-foreground">
        Para cada aula, informe quantas estações costumam ser usadas. O cálculo de emissões
        considera as estações não usadas como desligadas — slots livres não geram consumo.
      </p>

      {laboratories.length === 0 ? (
        <p className="text-sm text-muted-foreground italic">Nenhum laboratório ativo cadastrado.</p>
      ) : (
        <Tabs value={resolvedLabId ?? undefined} onValueChange={handleLabChange}>
          <div className="flex items-center gap-4">
            <TabsList>
              {laboratories.map((lab) => (
                <TabsTrigger key={lab.id} value={lab.id}>
                  {lab.name}
                </TabsTrigger>
              ))}
            </TabsList>

            {mode === 'grid' && canManage && laboratories.length > 1 && resolvedLabId && (
              <Select onValueChange={handleCopyFrom}>
                <SelectTrigger className="w-64">
                  <SelectValue placeholder="Copiar de outro laboratório..." />
                </SelectTrigger>
                <SelectContent>
                  {laboratories
                    .filter((l) => l.id !== resolvedLabId)
                    .map((lab) => (
                      <SelectItem key={lab.id} value={lab.id}>
                        {lab.name}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            )}
          </div>

          <div className="mt-4 inline-flex w-fit rounded-lg border bg-muted p-1">
            {(
              [
                ['grid', 'Grade semanal', CalendarDays],
                ['occurrences', 'Ocorrências por data', Clock],
              ] as const
            ).map(([value, label, Icon]) => (
              <button
                key={value}
                type="button"
                onClick={() => handleModeChange(value)}
                className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  mode === value ? 'bg-background shadow-sm' : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <Icon className="size-4" />
                {label}
              </button>
            ))}
          </div>

          {laboratories.map((lab) => (
            <TabsContent key={lab.id} value={lab.id} className="mt-4">
              {scheduleLoading ? (
                <p className="text-sm text-muted-foreground">Carregando grade...</p>
              ) : mode === 'occurrences' ? (
                <OccurrencesView
                  period={period}
                  laboratoryId={lab.id}
                  laboratoryName={lab.name}
                  capacity={lab.totalStations}
                  occupancy={serverOccupancy}
                  canEdit={canManage}
                  hasUnsavedGrid={dirty}
                />
              ) : (
                <div className="flex flex-col gap-6">
                  <div className="grid grid-cols-3 gap-4">
                    <Card size="sm">
                      <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-sm">
                          <BookOpen className="size-4 text-muted-foreground" />
                          Aulas por semana
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-2xl font-bold">{stats.classesPerWeek}</p>
                        <p className="text-xs text-muted-foreground">
                          de {stats.classesPossible} horários disponíveis
                        </p>
                      </CardContent>
                    </Card>
                    <Card size="sm">
                      <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-sm">
                          <Clock className="size-4 text-muted-foreground" />
                          Estações-hora por semana
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-2xl font-bold">
                          {Math.round(stats.stationHoursPerWeek).toLocaleString('pt-BR')} h
                        </p>
                        <p className="text-xs text-muted-foreground">
                          entra no cálculo de emissões do período
                        </p>
                      </CardContent>
                    </Card>
                    <Card size="sm">
                      <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-sm">
                          <Percent className="size-4 text-muted-foreground" />
                          Uso médio das estações
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <div className="flex flex-col gap-2">
                          <p className="text-2xl font-bold">{stats.averageUsage.toFixed(0)}%</p>
                          <Progress value={stats.averageUsage} className="h-2" />
                          <p className="text-xs text-muted-foreground">
                            média das aulas ocupadas · {capacity} estações no laboratório
                          </p>
                        </div>
                      </CardContent>
                    </Card>
                  </div>

                  {canManage && (
                    <div className="flex items-center justify-between">
                      <p className="text-xs text-muted-foreground">
                        Clique em uma aula para definir quantas estações são usadas.
                      </p>
                      <div className="flex gap-1">
                        <ApplyToAllButton capacity={capacity} onApply={handleApplyToAll} />
                        <Button variant="ghost" size="sm" onClick={() => setOccupancy({})}>
                          Limpar
                        </Button>
                      </div>
                    </div>
                  )}

                  <OccupationGrid
                    shifts={period.shifts}
                    occupancy={occupancy}
                    capacity={capacity}
                    readOnly={!canManage}
                    onSetStations={handleSetStations}
                  />

                  <p className="text-xs text-muted-foreground">
                    A grade é o padrão para todo o período letivo {period.name}. Ajustes em datas
                    específicas ficam em Ocorrências por data.
                  </p>
                </div>
              )}
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  )
}
