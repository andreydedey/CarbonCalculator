import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

// biome-ignore lint/suspicious/noArrayIndexKey: skeleton placeholders are static and never reorder
const k = (prefix: string, i: number) => `${prefix}-${i}`

export function CardListSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: count }, (_, i) => (
        <div key={k('cl', i)} className="rounded-lg border p-4 space-y-3">
          <div className="flex items-center justify-between">
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-5 w-20 rounded-full" />
          </div>
          <Skeleton className="h-4 w-64" />
          <div className="flex gap-4">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-4 w-24" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function CardGridSkeleton({ count = 4, columns = 2 }: { count?: number; columns?: number }) {
  const gridClass =
    columns === 3 ? 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3' : 'grid-cols-1 md:grid-cols-2'
  return (
    <div className={`grid gap-4 ${gridClass}`}>
      {Array.from({ length: count }, (_, i) => (
        <div key={k('cg', i)} className="rounded-lg border p-5 space-y-3">
          <div className="flex items-center justify-between">
            <Skeleton className="h-5 w-32" />
            <Skeleton className="h-5 w-16 rounded-full" />
          </div>
          <Skeleton className="h-4 w-48" />
          <div className="flex gap-4">
            <Skeleton className="h-4 w-20" />
            <Skeleton className="h-4 w-20" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function TableSkeleton({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="space-y-3">
      <div className="flex gap-4 px-4 py-2">
        {Array.from({ length: columns }, (_, i) => (
          <Skeleton key={k('th', i)} className="h-4 flex-1" />
        ))}
      </div>
      {Array.from({ length: rows }, (_, i) => (
        <div key={k('tr', i)} className="flex gap-4 px-4 py-3 border-t">
          {Array.from({ length: columns }, (_, j) => (
            <Skeleton key={k('td', j)} className="h-4 flex-1" />
          ))}
        </div>
      ))}
    </div>
  )
}

export function MetricCardSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="flex gap-4">
      {Array.from({ length: count }, (_, i) => (
        <Card key={k('mc', i)} className="flex-1 gap-3 p-5">
          <div className="flex items-center justify-between">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-4 w-4 rounded" />
          </div>
          <Skeleton className="h-7 w-20" />
          <Skeleton className="h-3 w-32" />
        </Card>
      ))}
    </div>
  )
}

export function SummaryCardsSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="flex gap-4">
      {Array.from({ length: count }, (_, i) => (
        <Card key={k('sc', i)} className="flex-1 gap-1 p-4">
          <Skeleton className="h-3 w-20" />
          <Skeleton className="h-6 w-12" />
        </Card>
      ))}
    </div>
  )
}

const BAR_HEIGHTS = [45, 72, 58, 81, 39, 66, 53, 88, 47, 74, 61, 35]

export function ChartSkeleton() {
  return (
    <div className="rounded-lg border p-6 space-y-4">
      <div className="flex items-center justify-between">
        <Skeleton className="h-5 w-40" />
        <Skeleton className="h-8 w-32 rounded-md" />
      </div>
      <div className="flex items-end gap-2 h-[300px] pt-8">
        {BAR_HEIGHTS.map((h, i) => (
          <div key={k('bar', i)} className="flex-1 flex flex-col justify-end">
            <Skeleton className="w-full rounded-t" style={{ height: `${h}%` }} />
          </div>
        ))}
      </div>
    </div>
  )
}

export function FormSkeleton() {
  return (
    <div className="w-full max-w-sm space-y-6">
      <div className="space-y-2 text-center">
        <Skeleton className="mx-auto h-7 w-32" />
        <Skeleton className="mx-auto h-4 w-56" />
      </div>
      <div className="space-y-4">
        <div className="space-y-2">
          <Skeleton className="h-4 w-12" />
          <Skeleton className="h-10 w-full rounded-md" />
        </div>
        <div className="space-y-2">
          <Skeleton className="h-4 w-12" />
          <Skeleton className="h-10 w-full rounded-md" />
        </div>
        <Skeleton className="h-10 w-full rounded-md" />
      </div>
      <div className="flex items-center gap-3">
        <Skeleton className="h-px flex-1" />
        <Skeleton className="h-3 w-6" />
        <Skeleton className="h-px flex-1" />
      </div>
      <Skeleton className="h-10 w-full rounded-md" />
    </div>
  )
}
