import { useMutation } from '@tanstack/react-query'
import type React from 'react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
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
      toast.success('Laboratório desativado.')
    },
  })

  const errorMessage = mutation.error
    ? mutation.error instanceof ApiError
      ? mutation.error.message
      : 'Não foi possível desativar o laboratório.'
    : undefined

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Desativar laboratório</AlertDialogTitle>
          <AlertDialogDescription>
            {buildDeactivateConfirmationMessage(laboratory.name)}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <FieldError message={errorMessage} />
        <AlertDialogFooter>
          <AlertDialogCancel disabled={mutation.isPending}>Cancelar</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={mutation.isPending}
            onClick={(e) => {
              e.preventDefault()
              mutation.mutate()
            }}
          >
            Desativar
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
