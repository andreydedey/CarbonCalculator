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
import { ApiError } from '@/lib/api/client'
import { deleteMonitor, type Monitor } from '@/lib/api/monitors'

interface DeleteMonitorDialogProps {
  monitor: Monitor
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeleted?: () => void
}

export const DeleteMonitorDialog: React.FC<DeleteMonitorDialogProps> = ({
  monitor,
  open,
  onOpenChange,
  onDeleted,
}) => {
  const deleteMutation = useMutation({
    mutationFn: () => deleteMonitor(monitor.id),
    onSuccess: () => {
      onOpenChange(false)
      onDeleted?.()
      toast.success('Monitor excluído.')
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir o monitor.')
    },
  })

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Excluir monitor</AlertDialogTitle>
          <AlertDialogDescription>
            Excluir &ldquo;{monitor.name}&rdquo;? Monitores vinculados a laboratórios não podem ser
            excluídos.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deleteMutation.isPending}>Cancelar</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            disabled={deleteMutation.isPending}
            onClick={(e) => {
              e.preventDefault()
              deleteMutation.mutate()
            }}
          >
            Excluir
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
