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

  return { open, data, openDialog, closeDialog }
}
