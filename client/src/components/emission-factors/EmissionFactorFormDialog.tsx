import type { UseFormReturn } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { MONTH_SHORT } from '@/lib/constants'
import type { EmissionFactorFormValues } from '@/lib/schemas/emissionFactorSchema'

const CURRENT_YEAR = new Date().getFullYear()
const YEAR_OPTIONS = Array.from({ length: 10 }, (_, i) => CURRENT_YEAR - 5 + i)

interface EmissionFactorFormDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  form: UseFormReturn<EmissionFactorFormValues>
  onSubmit: (values: EmissionFactorFormValues) => void
  isEditing: boolean
  isPending: boolean
}

export function EmissionFactorFormDialog({
  open,
  onOpenChange,
  form,
  onSubmit,
  isEditing,
  isPending,
}: EmissionFactorFormDialogProps) {
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = form

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[440px]">
        <DialogHeader>
          <DialogTitle>
            {isEditing ? 'Editar fator de emissão' : 'Novo fator de emissão'}
          </DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4 pt-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <Label>Ano</Label>
              <Select
                value={watch('year')?.toString()}
                onValueChange={(v) => setValue('year', Number(v), { shouldValidate: true })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Ano" />
                </SelectTrigger>
                <SelectContent>
                  {YEAR_OPTIONS.map((y) => (
                    <SelectItem key={y} value={y.toString()}>
                      {y}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {errors.year && (
                <span className="text-xs text-destructive">{errors.year.message}</span>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>Mês</Label>
              <Select
                value={watch('month')?.toString()}
                onValueChange={(v) => setValue('month', Number(v), { shouldValidate: true })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Mês" />
                </SelectTrigger>
                <SelectContent>
                  {MONTH_SHORT.slice(1).map((name, idx) => (
                    <SelectItem key={name} value={(idx + 1).toString()}>
                      {name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {errors.month && (
                <span className="text-xs text-destructive">{errors.month.message}</span>
              )}
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>Valor (kgCO₂/kWh)</Label>
            <Input type="number" step="0.000001" placeholder="0.000000" {...register('value')} />
            {errors.value && (
              <span className="text-xs text-destructive">{errors.value.message}</span>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>Fonte</Label>
            <Input placeholder="ex: MCTI — Fator médio SIN" {...register('source')} />
            {errors.source && (
              <span className="text-xs text-destructive">{errors.source.message}</span>
            )}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? 'Salvando…' : isEditing ? 'Salvar' : 'Cadastrar'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
