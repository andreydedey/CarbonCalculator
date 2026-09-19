import { useMutation } from '@tanstack/react-query'
import type React from 'react'
import { Button } from '@/components/ui/button'
import { FieldError } from '@/components/ui/field-error'
import { ApiError } from '@/lib/api/client'
import { deactivateLaboratory, type Laboratory } from '@/lib/api/laboratories'
import { buildDeactivateConfirmationMessage } from '@/lib/laboratories/buildDeactivateMessage'

interface DeactivateDialogProps {
  laboratory: Laboratory
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeactivated?: (laboratory: Laboratory) => void
}

export const DeactivateDialog: React.FC<DeactivateDialogProps> = ({
  laboratory,
  open,
  onOpenChange,
  onDeactivated,
}) => {
  const mutation = useMutation({
    mutationFn: () => deactivateLaboratory(laboratory.id),
    onSuccess: (deactivated) => {
      onDeactivated?.(deactivated)
      onOpenChange(false)
    },
  })

  if (!open) {
    return null
  }

  const errorMessage = mutation.error
    ? mutation.error instanceof ApiError
      ? mutation.error.message
      : 'Não foi possível desativar o laboratório.'
    : undefined

  return (
    <div
      role="alertdialog"
      aria-modal="true"
      className="fixed inset-0 flex items-center justify-center bg-black/40"
    >
      <div className="w-full max-w-sm rounded-lg border border-border bg-background p-4">
        <p className="text-sm">{buildDeactivateConfirmationMessage(laboratory.name)}</p>
        <FieldError message={errorMessage} />
        <div className="mt-4 flex justify-end gap-2">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={mutation.isPending}
          >
            Cancelar
          </Button>
          <Button
            variant="destructive"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending}
          >
            Desativar
          </Button>
        </div>
      </div>
    </div>
  )
}
