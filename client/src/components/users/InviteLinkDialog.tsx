import { Check, Copy } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

interface InviteLinkDialogProps {
  link: string | null
  onOpenChange: (open: boolean) => void
}

export function InviteLinkDialog({ link, onOpenChange }: InviteLinkDialogProps) {
  const [copied, setCopied] = useState(false)

  function handleCopy() {
    if (!link) return
    navigator.clipboard.writeText(link)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Dialog
      open={!!link}
      onOpenChange={(open) => {
        if (!open) setCopied(false)
        onOpenChange(open)
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Convite criado</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          Envie o link abaixo para o convidado. O link expira em 7 dias.
        </p>
        <div className="flex items-center gap-2">
          <Input value={link ?? ''} readOnly className="font-mono text-xs" />
          <Button variant="outline" size="icon" onClick={handleCopy}>
            {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
          </Button>
        </div>
        {copied && <p className="text-xs text-muted-foreground">Copiado!</p>}
      </DialogContent>
    </Dialog>
  )
}
