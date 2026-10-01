import { Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api/client'

interface ExportButtonProps {
  periodId: string
  periodName: string
}

export function ExportButton({ periodId, periodName }: ExportButtonProps) {
  async function handleExport() {
    const response = await api.get(`/academic-periods/${periodId}/emissions/export`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data], { type: 'text/csv; charset=UTF-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `emissoes-${periodName}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <Button variant="outline" size="sm" onClick={handleExport}>
      <Download className="size-4" />
      Exportar CSV
    </Button>
  )
}
