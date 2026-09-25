import { useMutation, useQueries, useQueryClient } from '@tanstack/react-query'
import { Clock, Moon, Sun, Sunset } from 'lucide-react'
import type React from 'react'
import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Checkbox } from '@/components/ui/checkbox'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { isApiError } from '@/lib/api/client'
import { type ScheduleBlock, getSchedule, replaceSchedule } from '@/lib/api/academic-periods'

const DAYS = [
  { value: 1, short: 'Seg' },
  { value: 2, short: 'Ter' },
  { value: 3, short: 'Qua' },
  { value: 4, short: 'Qui' },
  { value: 5, short: 'Sex' },
  { value: 6, short: 'Sáb' },
] as const

const SHIFTS = [
  { key: 'morning', label: 'Manhã', time: '07:30 – 11:50', startTime: '07:30', endTime: '11:50', icon: Sun },
  { key: 'afternoon', label: 'Tarde', time: '13:30 – 17:50', startTime: '13:30', endTime: '17:50', icon: Sunset },
  { key: 'evening', label: 'Noite', time: '18:30 – 22:00', startTime: '18:30', endTime: '22:00', icon: Moon },
] as const

type ShiftKey = (typeof SHIFTS)[number]['key']

type Lab = { id: string; name: string }

// Map a ScheduleBlock to the shift it belongs to (by matching start/end times)
function blockToShift(block: ScheduleBlock): ShiftKey | null {
  for (const shift of SHIFTS) {
    if (block.startTime === shift.startTime && block.endTime === shift.endTime) {
      return shift.key
    }
  }
  return null
}

// Build a lookup: shiftKey -> dayOfWeek -> boolean
function buildOccupancy(blocks: ScheduleBlock[]): Record<ShiftKey, Set<number>> {
  const map: Record<ShiftKey, Set<number>> = {
    morning: new Set(),
    afternoon: new Set(),
    evening: new Set(),
  }
  for (const block of blocks) {
    const shift = blockToShift(block)
    if (shift) map[shift].add(block.dayOfWeek)
  }
  return map
}

// Convert occupancy map back to ScheduleBlock[]
function occupancyToBlocks(occupancy: Record<ShiftKey, Set<number>>): ScheduleBlock[] {
  const blocks: ScheduleBlock[] = []
  for (const shift of SHIFTS) {
    for (const day of occupancy[shift.key]) {
      blocks.push({ dayOfWeek: day, startTime: shift.startTime, endTime: shift.endTime })
    }
  }
  return blocks.sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime))
}

interface ScheduleGridProps {
  periodId: string
  periodName: string
  laboratories: Lab[]
}

export const ScheduleGrid: React.FC<ScheduleGridProps> = ({
  periodId,
  periodName,
  laboratories,
}) => {
  const queryClient = useQueryClient()

  // Fetch schedule for all labs in parallel
  const scheduleQueries = useQueries({
    queries: laboratories.map((lab) => ({
      queryKey: ['schedule', periodId, lab.id],
      queryFn: () => getSchedule(periodId, lab.id),
    })),
  })

  // Local state: labId -> occupancy map
  const [occupancyMap, setOccupancyMap] = useState<Record<string, Record<ShiftKey, Set<number>>>>({})
  const [dirty, setDirty] = useState<Set<string>>(new Set())

  // Initialize local state from fetched data
  useEffect(() => {
    const newMap: Record<string, Record<ShiftKey, Set<number>>> = {}
    let changed = false
    for (let i = 0; i < laboratories.length; i++) {
      const lab = laboratories[i]
      const query = scheduleQueries[i]
      if (query.data && !occupancyMap[lab.id]) {
        newMap[lab.id] = buildOccupancy(query.data)
        changed = true
      }
    }
    if (changed) {
      setOccupancyMap((prev) => ({ ...prev, ...newMap }))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scheduleQueries.map((q) => q.dataUpdatedAt).join(',')])

  const toggle = useCallback((labId: string, shift: ShiftKey, day: number) => {
    setOccupancyMap((prev) => {
      const labOcc = prev[labId]
      if (!labOcc) return prev
      const newSet = new Set(labOcc[shift])
      if (newSet.has(day)) newSet.delete(day)
      else newSet.add(day)
      return { ...prev, [labId]: { ...labOcc, [shift]: newSet } }
    })
    setDirty((prev) => new Set(prev).add(labId))
  }, [])

  const saveMutation = useMutation({
    mutationFn: async () => {
      const dirtyLabs = Array.from(dirty)
      await Promise.all(
        dirtyLabs.map((labId) => {
          const blocks = occupancyToBlocks(occupancyMap[labId])
          return replaceSchedule(periodId, labId, blocks)
        }),
      )
    },
    onSuccess: () => {
      setDirty(new Set())
      queryClient.invalidateQueries({ queryKey: ['period-summary', periodId] })
      for (const labId of dirty) {
        queryClient.invalidateQueries({ queryKey: ['schedule', periodId, labId] })
      }
      toast.success('Ocupação salva com sucesso.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar a ocupação.')
    },
  })

  const isLoading = scheduleQueries.some((q) => q.isLoading)

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">Carregando grade de ocupação...</p>
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-0.5">
          <h2 className="font-heading text-xl font-bold">
            Ocupação dos Laboratórios — {periodName}
          </h2>
        </div>
        <button
          type="button"
          disabled={dirty.size === 0 || saveMutation.isPending}
          onClick={() => saveMutation.mutate()}
          className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50 disabled:pointer-events-none"
        >
          <Clock className="size-4" />
          Salvar Ocupação
        </button>
      </div>

      <div className="flex items-center gap-2 rounded-md border bg-muted/50 px-4 py-2.5 text-sm text-muted-foreground">
        <svg className="size-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="16" x2="12" y2="12" />
          <line x1="12" y1="8" x2="12.01" y2="8" />
        </svg>
        Marque os dias e turnos em que cada laboratório é utilizado. A ocupação vale para todo o período letivo.
      </div>

      <Tabs defaultValue="morning">
        <TabsList variant="line">
          {SHIFTS.map((shift) => {
            const Icon = shift.icon
            return (
              <TabsTrigger key={shift.key} value={shift.key} className="gap-1.5">
                <Icon className="size-3.5" />
                {shift.label}
                <span className="text-xs text-muted-foreground font-normal">{shift.time}</span>
              </TabsTrigger>
            )
          })}
        </TabsList>

        {SHIFTS.map((shift) => (
          <TabsContent key={shift.key} value={shift.key} className="mt-4">
            <ShiftSection
              shift={shift}
              laboratories={laboratories}
              occupancyMap={occupancyMap}
              onToggle={toggle}
            />
          </TabsContent>
        ))}
      </Tabs>
    </div>
  )
}

interface ShiftSectionProps {
  shift: (typeof SHIFTS)[number]
  laboratories: Lab[]
  occupancyMap: Record<string, Record<ShiftKey, Set<number>>>
  onToggle: (labId: string, shift: ShiftKey, day: number) => void
}

const ShiftSection: React.FC<ShiftSectionProps> = ({ shift, laboratories, occupancyMap, onToggle }) => {
  return (
    <div className="rounded-lg border bg-card overflow-hidden">
      {/* Column headers */}
      <div className="grid border-b px-5 py-2" style={{ gridTemplateColumns: '1fr repeat(6, 80px)' }}>
        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wider">
          Laboratório
        </span>
        {DAYS.map((d) => (
          <span key={d.value} className="text-xs font-medium text-muted-foreground text-center">
            {d.short}
          </span>
        ))}
      </div>

      {/* Lab rows */}
      {laboratories.map((lab, i) => {
        const labOcc = occupancyMap[lab.id]
        return (
          <div
            key={lab.id}
            className={`grid items-center px-5 py-2.5 ${i < laboratories.length - 1 ? 'border-b' : ''}`}
            style={{ gridTemplateColumns: '1fr repeat(6, 80px)' }}
          >
            <span className="text-sm font-medium truncate">{lab.name}</span>
            {DAYS.map((d) => {
              const checked = labOcc?.[shift.key]?.has(d.value) ?? false
              return (
                <div key={d.value} className="flex justify-center">
                  <Checkbox
                    checked={checked}
                    onCheckedChange={() => onToggle(lab.id, shift.key, d.value)}
                  />
                </div>
              )
            })}
          </div>
        )
      })}
    </div>
  )
}
