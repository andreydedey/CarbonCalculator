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
import { isApiError } from '@/lib/api/client'
import { deactivateLaboratory, type Laboratory } from '@/lib/api/laboratories'

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
    onError: (error) => {
      toast.error(
        isApiError(error) ? error.message : 'Não foi possível desativar o laboratório.',
      )
    },
  })

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Desativar laboratório</AlertDialogTitle>
          <AlertDialogDescription>
            Desativar &ldquo;{laboratory.name}&rdquo;? O laboratório e seu histórico serão
            preservados; ele deixará de aparecer na lista padrão, mas continuará disponível ao
            incluir inativos.
          </AlertDialogDescription>
        </AlertDialogHeader>
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
