import { z } from 'zod'

export const snapshotAggregateSchema = z.object({
  label: z.string(),
  startDate: z.string(),
  endDate: z.string(),
  periodId: z.string().uuid().optional(),
  totalEmissionKg: z.number(),
  totalEnergyKwh: z.number(),
  schoolDays: z.number().int(),
  stationCount: z.number().int(),
  avgEmissionFactor: z.number(),
  variationPct: z.number().nullable(),
})

export type SnapshotAggregate = z.infer<typeof snapshotAggregateSchema>
