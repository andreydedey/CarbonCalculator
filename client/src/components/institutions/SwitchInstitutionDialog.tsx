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
import type { Institution } from '@/lib/api/institutions'

interface SwitchInstitutionDialogProps {
  institution: Institution | null
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
}

export function SwitchInstitutionDialog({
  institution,
  onOpenChange,
  onConfirm,
}: SwitchInstitutionDialogProps) {
  return (
    <AlertDialog open={!!institution} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Trocar de instituição</AlertDialogTitle>
          <AlertDialogDescription>
            Você está prestes a trocar para{' '}
            <span className="font-medium text-foreground">
              {institution?.acronym} — {institution?.name}
            </span>
            . Deseja continuar?
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancelar</AlertDialogCancel>
          <AlertDialogAction onClick={onConfirm}>Trocar</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
