import { format, parse } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { CalendarIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'

interface DatePickerProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
  className?: string
  /** Earliest selectable date (yyyy-MM-dd) */
  minDate?: string
  /** Latest selectable date (yyyy-MM-dd) */
  maxDate?: string
}

function toDate(iso: string): Date {
  return parse(iso, 'yyyy-MM-dd', new Date())
}

export function DatePicker({
  value,
  onChange,
  placeholder = 'Selecione uma data',
  disabled,
  className,
  minDate,
  maxDate,
}: DatePickerProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          disabled={disabled}
          className={`w-full justify-start text-left font-normal ${!value ? 'text-muted-foreground' : ''} ${className ?? ''}`}
        >
          <CalendarIcon className="mr-2 size-4" />
          {value ? format(toDate(value), "dd 'de' MMMM 'de' yyyy", { locale: ptBR }) : placeholder}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={value ? toDate(value) : undefined}
          onSelect={(date) => onChange(date ? format(date, 'yyyy-MM-dd') : '')}
          disabled={(date) => {
            if (minDate && date < toDate(minDate)) return true
            if (maxDate && date > toDate(maxDate)) return true
            return false
          }}
          defaultMonth={value ? toDate(value) : minDate ? toDate(minDate) : undefined}
          locale={ptBR}
        />
      </PopoverContent>
    </Popover>
  )
}
