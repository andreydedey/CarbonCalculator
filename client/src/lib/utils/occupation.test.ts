import assert from 'node:assert/strict'
import { describe, test } from 'node:test'
import {
  addDays,
  clampDate,
  formatDate,
  formatDayMonth,
  isoDayOfWeek,
  startOfWeek,
  stationHours,
  usageTier,
} from './occupation.ts'

describe('usageTier', () => {
  test('classifies by share of the laboratory capacity', () => {
    assert.equal(usageTier(18, 18), 'high')
    assert.equal(usageTier(16, 18), 'high')
    assert.equal(usageTier(12, 18), 'medium')
    assert.equal(usageTier(8, 18), 'low')
  })

  test('treats a laboratory without stations as full', () => {
    assert.equal(usageTier(5, 0), 'high')
  })
})

describe('date helpers', () => {
  test('startOfWeek returns the Monday of the week', () => {
    assert.equal(startOfWeek('2025-04-24'), '2025-04-21')
    assert.equal(startOfWeek('2025-04-21'), '2025-04-21')
    assert.equal(startOfWeek('2025-04-27'), '2025-04-21')
  })

  test('addDays crosses month boundaries', () => {
    assert.equal(addDays('2025-04-28', 5), '2025-05-03')
    assert.equal(addDays('2025-03-01', -1), '2025-02-28')
  })

  test('isoDayOfWeek uses Monday = 1 and Sunday = 7', () => {
    assert.equal(isoDayOfWeek('2025-04-21'), 1)
    assert.equal(isoDayOfWeek('2025-04-27'), 7)
  })

  test('formats dates in Brazilian order', () => {
    assert.equal(formatDayMonth('2025-04-24'), '24/04')
    assert.equal(formatDate('2025-04-24'), '24/04/2025')
  })

  test('clampDate keeps the date inside the period', () => {
    assert.equal(clampDate('2025-01-10', '2025-03-10', '2025-07-18'), '2025-03-10')
    assert.equal(clampDate('2025-08-01', '2025-03-10', '2025-07-18'), '2025-07-18')
    assert.equal(clampDate('2025-04-24', '2025-03-10', '2025-07-18'), '2025-04-24')
  })
})

describe('stationHours', () => {
  test('sums stations times class duration', () => {
    assert.equal(stationHours([12, 18], 50), 25)
    assert.equal(stationHours([], 50), 0)
  })
})
