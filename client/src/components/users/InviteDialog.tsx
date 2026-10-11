import { Loader2 } from 'lucide-react'
import { Controller, type UseFormReturn } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FieldError } from '@/components/ui/field-error'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { InviteFormData } from '@/lib/schemas/inviteSchema'

interface InviteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  form: UseFormReturn<InviteFormData>
  onSubmit: (data: InviteFormData) => void
  isPending: boolean
  isAdmin: boolean
}

export function InviteDialog({
  open,
  onOpenChange,
  form,
  onSubmit,
  isPending,
  isAdmin,
}: InviteDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Convidar Membro</DialogTitle>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="invite-email">Email</Label>
            <Input
              id="invite-email"
              type="email"
              placeholder="email@universidade.br"
              {...form.register('email')}
            />
            {form.formState.errors.email && (
              <FieldError message={form.formState.errors.email.message} />
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="invite-role">Papel</Label>
            <Controller
              control={form.control}
              name="role"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {isAdmin && <SelectItem value="ADMIN">Admin Global</SelectItem>}
                    <SelectItem value="MANAGER">Gestor</SelectItem>
                    <SelectItem value="RESEARCHER">Pesquisador</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>
          <Button
            type="submit"
            className="w-full"
            disabled={!form.formState.isDirty || isPending}
          >
            {isPending && <Loader2 className="size-4 animate-spin" />}
            {isPending ? 'Enviando...' : 'Enviar Convite'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
