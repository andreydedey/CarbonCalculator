import { useId } from 'react'
import type { UseFormRegisterReturn } from 'react-hook-form'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** "Estações usadas [ 12 ] de 18" bound to a react-hook-form field. */
export function StationsField({
  registration,
  capacity,
  error,
  hint,
  autoFocus,
}: {
  registration: UseFormRegisterReturn
  capacity: number
  error?: string
  hint?: string
  autoFocus?: boolean
}) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id} className="text-xs">
        Estações usadas
      </Label>
      <div className="flex items-center gap-2">
        <Input
          id={id}
          type="number"
          autoFocus={autoFocus}
          min={1}
          max={capacity > 0 ? capacity : undefined}
          className="font-mono"
          aria-invalid={!!error}
          {...registration}
        />
        {capacity > 0 && (
          <span className="shrink-0 text-xs text-muted-foreground">de {capacity}</span>
        )}
      </div>
      <FieldError message={error} />
      {hint && <p className="text-[11px] text-muted-foreground">{hint}</p>}
    </div>
  )
}
