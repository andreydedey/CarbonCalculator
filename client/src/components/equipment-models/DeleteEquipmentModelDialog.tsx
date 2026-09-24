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
import { deleteEquipmentModel, type EquipmentModel } from '@/lib/api/equipment-models'

interface DeleteEquipmentModelDialogProps {
  model: EquipmentModel
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeleted?: () => void
}

export const DeleteEquipmentModelDialog: React.FC<DeleteEquipmentModelDialogProps> = ({
  model,
  open,
  onOpenChange,
  onDeleted,
}) => {
  const deleteMutation = useMutation({
    mutationFn: () => deleteEquipmentModel(model.id),
    onSuccess: () => {
      onOpenChange(false)
      onDeleted?.()
      toast.success('Modelo excluído.')
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : 'Não foi possível excluir o modelo.')
    },
  })

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Excluir modelo</AlertDialogTitle>
          <AlertDialogDescription>
            Excluir &ldquo;{model.name}&rdquo;? Modelos vinculados a laboratórios não podem ser
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
