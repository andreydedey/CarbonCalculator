import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { isApiError } from '@/lib/api/client'
import { type ScheduleBlock, getSchedule, replaceSchedule } from '@/lib/api/academic-periods'

const DAY_LABELS: Record<number, string> = {
  1: 'Segunda',
  2: 'Terça',
  3: 'Quarta',
  4: 'Quinta',
  5: 'Sexta',
  6: 'Sábado',
}

interface ScheduleGridProps {
  periodId: string
  laboratoryId: string
  laboratoryName: string
}

export const ScheduleGrid: React.FC<ScheduleGridProps> = ({
  periodId,
  laboratoryId,
  laboratoryName,
}) => {
  const queryClient = useQueryClient()
  const [blocks, setBlocks] = useState<ScheduleBlock[]>([])
  const [newDay, setNewDay] = useState('1')
  const [newStart, setNewStart] = useState('08:00')
  const [newEnd, setNewEnd] = useState('12:00')

  useQuery({
    queryKey: ['schedule', periodId, laboratoryId],
    queryFn: async () => {
      const data = await getSchedule(periodId, laboratoryId)
      setBlocks(data)
      return data
    },
  })

  const saveMutation = useMutation({
    mutationFn: (updated: ScheduleBlock[]) => replaceSchedule(periodId, laboratoryId, updated),
    onSuccess: (saved) => {
      setBlocks(saved)
      queryClient.invalidateQueries({ queryKey: ['period-summary', periodId] })
      toast.success(`Grade de ${laboratoryName} salva.`)
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar a grade.')
    },
  })

  function addBlock() {
    const block: ScheduleBlock = {
      dayOfWeek: Number(newDay),
      startTime: newStart,
      endTime: newEnd,
    }
    const updated = [...blocks, block].sort(
      (a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime),
    )
    setBlocks(updated)
  }

  function removeBlock(index: number) {
    setBlocks(blocks.filter((_, i) => i !== index))
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-semibold">{laboratoryName}</h4>
        <Button
          size="sm"
          variant="outline"
          disabled={saveMutation.isPending}
          onClick={() => saveMutation.mutate(blocks)}
        >
          Salvar Grade
        </Button>
      </div>

      <div className="flex items-end gap-2">
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">Dia</label>
          <Select value={newDay} onValueChange={setNewDay}>
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(DAY_LABELS).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">Início</label>
          <Input
            type="time"
            className="w-28"
            value={newStart}
            onChange={(e) => setNewStart(e.target.value)}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">Fim</label>
          <Input
            type="time"
            className="w-28"
            value={newEnd}
            onChange={(e) => setNewEnd(e.target.value)}
          />
        </div>
        <Button type="button" variant="outline" size="icon" onClick={addBlock}>
          <Plus className="size-4" />
        </Button>
      </div>

      {blocks.length === 0 ? (
        <p className="text-xs text-muted-foreground italic py-2">
          Nenhum horário cadastrado para este laboratório.
        </p>
      ) : (
        <div className="rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="px-4 py-2 text-left font-medium">Dia</th>
                <th className="px-4 py-2 text-left font-medium">Horário</th>
                <th className="px-4 py-2 w-10" />
              </tr>
            </thead>
            <tbody>
              {blocks.map((block, i) => (
                <tr key={`${block.dayOfWeek}-${block.startTime}`} className="border-b last:border-0">
                  <td className="px-4 py-2">{DAY_LABELS[block.dayOfWeek]}</td>
                  <td className="px-4 py-2 font-mono text-xs">
                    {block.startTime} — {block.endTime}
                  </td>
                  <td className="px-4 py-2">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-7"
                      onClick={() => removeBlock(i)}
                    >
                      <Trash2 className="size-3.5 text-destructive" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
