import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Trash2 } from 'lucide-react'
import type React from 'react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { DatePicker } from '@/components/ui/date-picker'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  type AcademicPeriod,
  getHolidays,
  type Holiday,
  type HolidayType,
  replaceHolidays,
} from '@/lib/api/academic-periods'
import { isApiError } from '@/lib/api/client'

interface HolidayEditorProps {
  period: AcademicPeriod
  addDialogOpen?: boolean
  onAddDialogOpenChange?: (open: boolean) => void
}

const HOLIDAY_TYPE_LABELS: Record<HolidayType, string> = {
  NATIONAL: 'Feriado Nacional',
  STATE: 'Feriado Estadual',
  MUNICIPAL: 'Feriado Municipal',
  RECESS: 'Recesso',
}

const HOLIDAY_TYPE_BADGE_STYLES: Record<HolidayType, string> = {
  NATIONAL: 'bg-[#DEECE2] text-[#1B5238]',
  STATE: 'bg-[#E0E7FF] text-[#3730A3]',
  MUNICIPAL: 'bg-[#F0E4FF] text-[#6B21A8]',
  RECESS: 'bg-[#FEF9C3] text-[#854D0E]',
}

function formatDateShort(dateStr: string): string {
  const [, month, day] = dateStr.split('-')
  return `${day}/${month}`
}

export const HolidayEditor: React.FC<HolidayEditorProps> = ({
  period,
  addDialogOpen: externalOpen,
  onAddDialogOpenChange,
}) => {
  const queryClient = useQueryClient()
  const [internalOpen, setInternalOpen] = useState(false)

  const addDialogOpen = externalOpen ?? internalOpen
  const setAddDialogOpen = (open: boolean) => {
    setInternalOpen(open)
    onAddDialogOpenChange?.(open)
  }
  const [newDate, setNewDate] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [newType, setNewType] = useState<HolidayType>('NATIONAL')

  const { data: holidays = [], refetch } = useQuery({
    queryKey: ['holidays', period.id],
    queryFn: () => getHolidays(period.id),
  })

  const saveMutation = useMutation({
    mutationFn: (updated: Holiday[]) => replaceHolidays(period.id, updated),
    onSuccess: () => {
      refetch()
      queryClient.invalidateQueries({ queryKey: ['academic-periods'] })
      queryClient.invalidateQueries({ queryKey: ['period-summary', period.id] })
      toast.success('Feriados salvos.')
    },
    onError: (error) => {
      toast.error(isApiError(error) ? error.message : 'Não foi possível salvar os feriados.')
    },
  })

  function addHoliday() {
    if (!newDate || !newDescription) {
      toast.error('Informe data e descrição.')
      return
    }
    if (holidays.some((h) => h.date === newDate)) {
      toast.error('Já existe um feriado nesta data.')
      return
    }
    const updated = [
      ...holidays,
      { date: newDate, description: newDescription, type: newType },
    ].sort((a, b) => a.date.localeCompare(b.date))
    setAddDialogOpen(false)
    setNewDate('')
    setNewDescription('')
    setNewType('NATIONAL')
    saveMutation.mutate(updated)
  }

  function removeHoliday(date: string) {
    const updated = holidays.filter((h) => h.date !== date)
    saveMutation.mutate(updated)
  }

  return (
    <div className="flex flex-col gap-3">
      {holidays.length === 0 ? (
        <p className="text-sm text-muted-foreground italic py-4 text-center">
          Nenhum feriado cadastrado.
        </p>
      ) : (
        <div className="rounded-lg border">
          {/* Header */}
          <div className="flex items-center border-b bg-muted/50 px-6 py-2.5">
            <span className="w-40 shrink-0 text-[11px] font-medium tracking-wide text-muted-foreground">
              DATA
            </span>
            <span className="flex-1 text-[11px] font-medium tracking-wide text-muted-foreground">
              FERIADO / RECESSO
            </span>
            <span className="w-44 shrink-0 text-[11px] font-medium tracking-wide text-muted-foreground">
              TIPO
            </span>
          </div>

          {/* Rows */}
          {holidays.map((holiday) => (
            <div
              key={holiday.date}
              className="group flex items-center border-b last:border-0 px-6 py-2.5"
            >
              <span className="w-40 shrink-0 text-sm text-muted-foreground">
                {formatDateShort(holiday.date)}
              </span>
              <span className="flex-1 text-sm">{holiday.description}</span>
              <div className="w-44 shrink-0 flex items-center gap-2">
                <span
                  className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${HOLIDAY_TYPE_BADGE_STYLES[holiday.type]}`}
                >
                  {HOLIDAY_TYPE_LABELS[holiday.type]}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-6 opacity-0 group-hover:opacity-100 transition-opacity"
                  onClick={() => removeHoliday(holiday.date)}
                >
                  <Trash2 className="size-3 text-destructive" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Dialog open={addDialogOpen} onOpenChange={setAddDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Adicionar Feriado</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Data</span>
              <DatePicker value={newDate} onChange={setNewDate} />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="holiday-desc" className="text-sm font-medium">
                Descrição
              </label>
              <Input
                id="holiday-desc"
                placeholder="Ex: Sexta-feira Santa"
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label htmlFor="holiday-type" className="text-sm font-medium">
                Tipo
              </label>
              <Select value={newType} onValueChange={(v) => setNewType(v as HolidayType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="NATIONAL">Feriado Nacional</SelectItem>
                  <SelectItem value="STATE">Feriado Estadual</SelectItem>
                  <SelectItem value="MUNICIPAL">Feriado Municipal</SelectItem>
                  <SelectItem value="RECESS">Recesso</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddDialogOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={addHoliday}>Adicionar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
