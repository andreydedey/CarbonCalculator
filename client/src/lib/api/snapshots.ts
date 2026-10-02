import { api } from './client.ts'
import type { PageParams, PageResponse } from './types'

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

export type ListSnapshotHistoryParams = ListSnapshotsParams & PageParams

function buildSnapshotQuery({
  granularity,
  startDate,
  endDate,
}: ListSnapshotsParams): URLSearchParams {
  const query = new URLSearchParams({ granularity })
  if (startDate) query.set('startDate', startDate)
  if (endDate) query.set('endDate', endDate)
  return query
}

export async function listSnapshots(params: ListSnapshotsParams): Promise<SnapshotAggregateDTO[]> {
  return api
    .get<SnapshotAggregateDTO[]>(`/snapshots?${buildSnapshotQuery(params)}`)
    .then((r) => r.data)
}

export async function listSnapshotHistory({
  page = 0,
  size = 20,
  ...params
}: ListSnapshotHistoryParams): Promise<PageResponse<SnapshotAggregateDTO>> {
  const query = buildSnapshotQuery(params)
  query.set('page', String(page))
  query.set('size', String(size))
  return api
    .get<PageResponse<SnapshotAggregateDTO>>(`/snapshots/history?${query}`)
    .then((r) => r.data)
}
