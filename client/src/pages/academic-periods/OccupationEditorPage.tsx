import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BookOpen, Clock, Percent, Save } from 'lucide-react'
import type React from 'react'
import { useCallback, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { type Occupancy, OccupationGrid } from '@/components/academic-periods/OccupationGrid'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  getAcademicPeriod,
  getSchedule,
  replaceSchedule,
  type ScheduleInput,
} from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'
import { listLaboratories } from '@/lib/api/laboratories'

function buildOccupancy(
  entries: { shiftId: string; dayOfWeek: number; occupiedSlots: number[] }[],
): Occupancy {
  const map: Occupancy = {}
  for (const entry of entries) {
    if (!map[entry.shiftId]) map[entry.shiftId] = {}
    map[entry.shiftId][entry.dayOfWeek] = new Set(entry.occupiedSlots)
  }
  return map
}

function occupancyToInputs(occupancy: Occupancy): ScheduleInput[] {
  const inputs: ScheduleInput[] = []
  for (const [shiftId, days] of Object.entries(occupancy)) {
    for (const [dayStr, slots] of Object.entries(days)) {
      const occupiedSlots = Array.from(slots).sort((a, b) => a - b)
      if (occupiedSlots.length > 0) {
        inputs.push({ shiftId, dayOfWeek: Number(dayStr), occupiedSlots })
      }
    }
  }
  return inputs
}

export const OccupationEditorPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [selectedLabId, setSelectedLabId] = useState<string | null>(null)
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
  const dataKey = `${resolvedLabId}-${scheduleEntries?.length ?? ''}`
  if (dataKey !== prevDataRef.current && scheduleEntries) {
    prevDataRef.current = dataKey
    setOccupancy(serverOccupancy)
  }

  const dirty = useMemo(() => {
    const serverKeys = Object.keys(serverOccupancy)
    const localKeys = Object.keys(occupancy)
    const allShifts = new Set([...serverKeys, ...localKeys])
    for (const shiftId of allShifts) {
      const serverDays = serverOccupancy[shiftId] ?? {}
      const localDays = occupancy[shiftId] ?? {}
      const allDays = new Set([...Object.keys(serverDays), ...Object.keys(localDays)])
      for (const day of allDays) {
        const serverSlots = serverDays[Number(day)] ?? new Set()
        const localSlots = localDays[Number(day)] ?? new Set()
        if (serverSlots.size !== localSlots.size) return true
        for (const s of serverSlots) {
          if (!localSlots.has(s)) return true
        }
      }
    }
    return false
  }, [occupancy, serverOccupancy])

  const handleToggle = useCallback((shiftId: string, dayOfWeek: number, slot: number) => {
    setOccupancy((prev) => {
      const shiftDays = { ...(prev[shiftId] ?? {}) }
      const daySlots = new Set(shiftDays[dayOfWeek] ?? [])
      if (daySlots.has(slot)) daySlots.delete(slot)
      else daySlots.add(slot)
      shiftDays[dayOfWeek] = daySlots
      return { ...prev, [shiftId]: shiftDays }
    })
  }, [])

  const handleLabChange = useCallback((labId: string) => {
    setSelectedLabId(labId)
    prevDataRef.current = null
  }, [])

  const handleCopyFrom = useCallback(
    async (sourceLabId: string) => {
      if (!id) return
      try {
        const entries = await getSchedule(id, sourceLabId)
        setOccupancy(buildOccupancy(entries))
        toast.success('Ocupação copiada. Salve para confirmar.')
      } catch {
        toast.error('Não foi possível copiar a ocupação.')
      }
    },
    [id],
  )

  const saveMutation = useMutation({
    mutationFn: () => replaceSchedule(id!, resolvedLabId!, occupancyToInputs(occupancy)),
    onSuccess: () => {
      refetchSchedule()
      queryClient.invalidateQueries({ queryKey: ['period-summary', id] })
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
    let totalOccupied = 0
    let totalPossible = 0
    let totalMinutes = 0

    for (const shift of enabledShifts) {
      totalPossible += shift.classesPerDay * shift.activeDays.length
      const shiftOcc = occupancy[shift.id] ?? {}
      for (const day of shift.activeDays) {
        const count = shiftOcc[day]?.size ?? 0
        totalOccupied += count
        totalMinutes += count * shift.classDurationMinutes
      }
    }

    return {
      classesPerWeek: totalOccupied,
      hoursPerWeek: totalMinutes / 60,
      occupationRate: totalPossible > 0 ? (totalOccupied / totalPossible) * 100 : 0,
    }
  }, [enabledShifts, occupancy])

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
        <div className="ml-auto">
          <Button disabled={!dirty || saveMutation.isPending} onClick={() => saveMutation.mutate()}>
            <Save className="size-4" />
            Salvar Ocupação
          </Button>
        </div>
      </div>

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

            {laboratories.length > 1 && resolvedLabId && (
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

          {laboratories.map((lab) => (
            <TabsContent key={lab.id} value={lab.id} className="mt-4">
              {scheduleLoading ? (
                <p className="text-sm text-muted-foreground">Carregando grade...</p>
              ) : (
                <div className="flex flex-col gap-6">
                  {/* Stats cards */}
                  <div className="grid grid-cols-3 gap-4">
                    <Card size="sm">
                      <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-sm">
                          <BookOpen className="size-4 text-muted-foreground" />
                          Aulas/Semana
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-2xl font-bold">{stats.classesPerWeek}</p>
                      </CardContent>
                    </Card>
                    <Card size="sm">
                      <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-sm">
                          <Clock className="size-4 text-muted-foreground" />
                          Horas/Semana
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-2xl font-bold">{stats.hoursPerWeek.toFixed(1)}h</p>
                      </CardContent>
                    </Card>
                    <Card size="sm">
                      <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-sm">
                          <Percent className="size-4 text-muted-foreground" />
                          Taxa de Ocupação
                        </CardTitle>
                      </CardHeader>
                      <CardContent>
                        <div className="flex flex-col gap-2">
                          <p className="text-2xl font-bold">{stats.occupationRate.toFixed(0)}%</p>
                          <Progress value={stats.occupationRate} className="h-2" />
                        </div>
                      </CardContent>
                    </Card>
                  </div>

                  <OccupationGrid
                    shifts={period.shifts}
                    occupancy={occupancy}
                    onToggle={handleToggle}
                  />
                </div>
              )}
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  )
}
