import { api } from './client.ts'

export type SnapshotAggregateDTO = {
  label: string
  startDate: string
  endDate: string
  periodId?: string
  totalEmissionKg: number
  totalEnergyKwh: number
  schoolDays: number
  stationCount: number
  avgEmissionFactor: number
  variationPct: number | null
}

export type Granularity = 'daily' | 'weekly' | 'monthly' | 'period'

export type ListSnapshotsParams = {
  granularity: Granularity
  startDate?: string
  endDate?: string
}

export async function listSnapshots(params: ListSnapshotsParams): Promise<SnapshotAggregateDTO[]> {
  const query = new URLSearchParams({ granularity: params.granularity })
  if (params.startDate) query.set('startDate', params.startDate)
  if (params.endDate) query.set('endDate', params.endDate)
  return api.get<SnapshotAggregateDTO[]>(`/snapshots?${query.toString()}`)
}
