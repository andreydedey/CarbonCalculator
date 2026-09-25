import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { isApiError } from '@/lib/api/client'
import {
  type AcademicPeriod,
  type Holiday,
  replaceHolidays,
} from '@/lib/api/academic-periods'

interface HolidayEditorProps {
  period: AcademicPeriod
}

export const HolidayEditor: React.FC<HolidayEditorProps> = ({ period }) => {
  const queryClient = useQueryClient()
  const [holidays, setHolidays] = useState<Holiday[]>([])
  const [newDate, setNewDate] = useState('')
  const [newDescription, setNewDescription] = useState('')

  const saveMutation = useMutation({
    mutationFn: (updated: Holiday[]) => replaceHolidays(period.id, updated),
    onSuccess: (saved) => {
      setHolidays(saved)
      queryClient.invalidateQueries({ queryKey: ['academic-periods'] })
      queryClient.invalidateQueries({ queryKey: ['period-summary', period.id] })
      toast.success('Feriados salvos.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar os feriados.')
    },
  })

  function addHoliday() {
    if (!newDate) return
    if (holidays.some((h) => h.date === newDate)) {
      toast.error('Já existe um feriado nesta data.')
      return
    }
    const updated = [...holidays, { date: newDate, description: newDescription || undefined }]
      .sort((a, b) => a.date.localeCompare(b.date))
    setHolidays(updated)
    setNewDate('')
    setNewDescription('')
  }

  function removeHoliday(date: string) {
    setHolidays(holidays.filter((h) => h.date !== date))
  }

  function formatDate(dateStr: string): string {
    const [year, month, day] = dateStr.split('-')
    return `${day}/${month}/${year}`
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">Feriados e Recessos</h3>
        <Button
          size="sm"
          disabled={saveMutation.isPending}
          onClick={() => saveMutation.mutate(holidays)}
        >
          Salvar Feriados
        </Button>
      </div>

      <div className="flex items-end gap-2">
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">Data</label>
          <Input
            type="date"
            className="w-40"
            value={newDate}
            min={period.startDate}
            max={period.endDate}
            onChange={(e) => setNewDate(e.target.value)}
          />
        </div>
        <div className="flex flex-1 flex-col gap-1">
          <label className="text-xs text-muted-foreground">Descrição (opcional)</label>
          <Input
            placeholder="Ex: Sexta-feira Santa"
            value={newDescription}
            onChange={(e) => setNewDescription(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addHoliday())}
          />
        </div>
        <Button type="button" variant="outline" size="icon" onClick={addHoliday}>
          <Plus className="size-4" />
        </Button>
      </div>

      {holidays.length === 0 ? (
        <p className="text-xs text-muted-foreground italic py-4 text-center">
          Nenhum feriado cadastrado. Adicione feriados e clique em "Salvar Feriados".
        </p>
      ) : (
        <div className="rounded-lg border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="px-4 py-2 text-left font-medium">Data</th>
                <th className="px-4 py-2 text-left font-medium">Descrição</th>
                <th className="px-4 py-2 w-10" />
              </tr>
            </thead>
            <tbody>
              {holidays.map((holiday) => (
                <tr key={holiday.date} className="border-b last:border-0">
                  <td className="px-4 py-2 font-mono text-xs">{formatDate(holiday.date)}</td>
                  <td className="px-4 py-2 text-muted-foreground">{holiday.description ?? '—'}</td>
                  <td className="px-4 py-2">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-7"
                      onClick={() => removeHoliday(holiday.date)}
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
