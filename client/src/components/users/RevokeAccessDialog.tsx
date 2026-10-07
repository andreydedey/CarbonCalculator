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
import { revokeAccess, type UserMember } from '@/lib/api/users'

interface RevokeAccessDialogProps {
  member: UserMember
  open: boolean
  onOpenChange: (open: boolean) => void
  onRevoked?: () => void
}

export const RevokeAccessDialog: React.FC<RevokeAccessDialogProps> = ({
  member,
  open,
  onOpenChange,
  onRevoked,
}) => {
  const mutation = useMutation({
    mutationFn: () => revokeAccess(member.id),
    onSuccess: () => {
      onOpenChange(false)
      onRevoked?.()
      toast.success('Acesso revogado')
    },
    onError: (err) => {
      toast.error(err instanceof ApiError ? err.message : 'Erro ao revogar acesso')
    },
  })

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Revogar acesso</AlertDialogTitle>
          <AlertDialogDescription>
            Revogar o acesso de{' '}
            <span className="font-medium text-foreground">{member.name ?? member.email}</span>?
            Esta ação é irreversível e o membro perderá o acesso imediatamente.
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
            Revogar
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
