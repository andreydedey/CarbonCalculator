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
import { deleteLaboratory, type Laboratory } from '@/lib/api/laboratories'

interface DeleteDialogProps {
  laboratory: Laboratory
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeleted?: () => void
}

export const DeleteDialog: React.FC<DeleteDialogProps> = ({
  laboratory,
  open,
  onOpenChange,
  onDeleted,
}) => {
  const mutation = useMutation({
    mutationFn: () => deleteLaboratory(laboratory.id),
    onSuccess: () => {
      onOpenChange(false)
      onDeleted?.()
      toast.success('Laboratório excluído.')
    },
  })

  const errorMessage = mutation.error
    ? mutation.error instanceof ApiError
      ? mutation.error.message
      : 'Não foi possível excluir o laboratório.'
    : undefined

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Excluir laboratório</AlertDialogTitle>
          <AlertDialogDescription>
            Excluir &ldquo;{laboratory.name}&rdquo;? Esta ação é irreversível e todos os dados
            associados serão removidos permanentemente.
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
            Excluir
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
