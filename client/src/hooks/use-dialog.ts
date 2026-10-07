import { useState } from 'react'

interface DialogState<T> {
  open: boolean
  data: T | null
}

export function useDialog<T = null>() {
  const [{ open, data }, setState] = useState<DialogState<T>>({ open: false, data: null })

  function openDialog(data: T | null = null) {
    setState({ open: true, data })
  }

  function closeDialog() {
    setState({ open: false, data: null })
  }

  // Ready to pass to a Dialog's onOpenChange: only closing comes from the dialog itself
  function onOpenChange(next: boolean) {
    if (!next) closeDialog()
  }

  return { open, data, openDialog, closeDialog, onOpenChange }
}

/**
 * One form dialog used for both "create" (opened by the page header) and "edit"
 * (opened from a list row with the item to edit).
 */
export function useFormDialog<T>({
  createOpen,
  onCreateOpenChange,
}: {
  createOpen?: boolean
  onCreateOpenChange?: (open: boolean) => void
}) {
  const edit = useDialog<T>()

  function close() {
    onCreateOpenChange?.(false)
    edit.closeDialog()
  }

  return {
    open: (createOpen ?? false) || edit.open,
    editing: edit.data,
    openEdit: (item: T) => edit.openDialog(item),
    close,
    onOpenChange: (next: boolean) => {
      if (!next) close()
    },
  }
}
