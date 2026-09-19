import { useQuery } from '@tanstack/react-query'
import type React from 'react'
import { useState } from 'react'
import { listLaboratories } from '@/lib/api/laboratories'
import { buildListQuery, toViewModel } from '@/lib/laboratories/laboratoryViewModel'

export const LaboratoryList: React.FC = () => {
  const [showInactive, setShowInactive] = useState(false)

  const { data: laboratories = [], isLoading } = useQuery({
    queryKey: ['laboratories', showInactive],
    queryFn: () => listLaboratories(buildListQuery(showInactive)),
    select: (data) => data.map(toViewModel),
  })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h1 className="font-heading text-lg font-semibold">Laboratórios</h1>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            checked={showInactive}
            onChange={(event) => setShowInactive(event.target.checked)}
          />
          Mostrar inativos
        </label>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Carregando...</p>
      ) : laboratories.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nenhum laboratório encontrado.</p>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {laboratories.map((laboratory) => (
            <div key={laboratory.id} className="rounded-lg border border-border bg-background p-4">
              <div className="flex items-center justify-between">
                <p className="font-medium">{laboratory.name}</p>
                <span
                  className={
                    laboratory.active
                      ? 'rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary'
                      : 'rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground'
                  }
                >
                  {laboratory.statusLabel}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
