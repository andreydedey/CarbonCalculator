import { api } from './client.ts'
import type { PageResponse } from './types'

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
  return api.get<SnapshotAggregateDTO[]>(`/snapshots?${query.toString()}`).then((r) => r.data)
}

export type ListSnapshotHistoryParams = ListSnapshotsParams & {
  page?: number
  size?: number
}

// Paginated, most-recent-first history for the "Histórico de Emissões" table (the
// backend caps size at 20 regardless of what is requested here).
export async function listSnapshotHistory(
  params: ListSnapshotHistoryParams,
): Promise<PageResponse<SnapshotAggregateDTO>> {
  const { page = 0, size = 20, granularity, startDate, endDate } = params
  const query = new URLSearchParams({ granularity, page: String(page), size: String(size) })
  if (startDate) query.set('startDate', startDate)
  if (endDate) query.set('endDate', endDate)
  return api
    .get<PageResponse<SnapshotAggregateDTO>>(`/snapshots/history?${query.toString()}`)
    .then((r) => r.data)
}
