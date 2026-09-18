/**
 * Diálogo de confirmação de desativação de laboratório (US-005, AC-011).
 *
 * A lógica pura abaixo (antes do marcador `@pure-logic-boundary`) não usa
 * JSX, para poder ser verificada com `node --test` sem bundler.
 */

/**
 * Mensagem de confirmação exibida antes de desativar. Deixa explícito que
 * a operação preserva o laboratório e seu histórico — não é uma exclusão
 * (@spec:AC-011).
 */
export function buildDeactivateConfirmationMessage(laboratoryName: string): string {
  return `Desativar "${laboratoryName}"? O laboratório e seu histórico serão preservados; ele deixará de aparecer na lista padrão, mas continuará disponível ao incluir inativos.`
}

// @pure-logic-boundary

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api/client'
import { deactivateLaboratory, type Laboratory } from '@/lib/api/laboratories'

export function DeactivateDialog({
  laboratory,
  open,
  onOpenChange,
  onDeactivated,
}: {
  laboratory: Laboratory
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeactivated?: (laboratory: Laboratory) => void
}) {
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!open) {
    return null
  }

  async function handleConfirm() {
    setError(null)
    setIsSubmitting(true)
    try {
      const deactivated = await deactivateLaboratory(laboratory.id)
      onDeactivated?.(deactivated)
      onOpenChange(false)
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Não foi possível desativar o laboratório.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div
      role="alertdialog"
      aria-modal="true"
      className="fixed inset-0 flex items-center justify-center bg-black/40"
    >
      <div className="w-full max-w-sm rounded-lg border border-border bg-background p-4">
        <p className="text-sm">{buildDeactivateConfirmationMessage(laboratory.name)}</p>
        {error && (
          <p role="alert" className="mt-2 text-sm text-destructive">
            {error}
          </p>
        )}
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
            Cancelar
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={isSubmitting}>
            Desativar
          </Button>
        </div>
      </div>
    </div>
  )
}
