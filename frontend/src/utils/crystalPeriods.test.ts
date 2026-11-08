import { describe, expect, it } from 'vitest'
import { crystalAxisMax, crystalChartPeriods, crystalWindowRange } from './crystalPeriods'

describe('crystal periods', () => {
  it('includes the current Thursday to Wednesday week in both windows', () => {
    expect(crystalWindowRange('2026-10-10', 4)).toEqual({ dateFrom: '2026-09-17', dateTo: '2026-10-14' })
    expect(crystalWindowRange('2026-10-10', 12)).toEqual({ dateFrom: '2026-07-23', dateTo: '2026-10-14' })
  })

  it('fills missing weeks and makes four weekly bars', () => {
    const periods = crystalChartPeriods([{ weekStart: '2026-10-01', totalIncome: 100, weeklyIncome: 70, monthlyIncome: 30, bossDetails: [] }], '2026-09-17', 4)
    expect(periods.map(({ totalIncome }) => totalIncome)).toEqual([0, 0, 100, 0])
    expect(periods.map(({ weeklyIncome }) => weeklyIncome)).toEqual([0, 0, 70, 0])
    expect(periods.map(({ monthlyIncome }) => monthlyIncome)).toEqual([0, 0, 30, 0])
    expect(periods[0].weekStart).toBe('2026-09-17 ~ 2026-09-23')
  })

  it('makes three consecutive four-week sums across a year boundary', () => {
    const periods = crystalChartPeriods([
      { weekStart: '2025-12-25', totalIncome: 10, weeklyIncome: 7, monthlyIncome: 3, bossDetails: [] },
      { weekStart: '2026-01-01', totalIncome: 20, weeklyIncome: 12, monthlyIncome: 8, bossDetails: [] },
    ], '2025-12-04', 12)
    expect(periods).toHaveLength(3)
    expect(periods[0].totalIncome).toBe(10)
    expect(periods[1].totalIncome).toBe(20)
    expect(periods[2].totalIncome).toBe(0)
    expect(periods.map(({ weeklyIncome }) => weeklyIncome)).toEqual([7, 12, 0])
    expect(periods.map(({ monthlyIncome }) => monthlyIncome)).toEqual([3, 8, 0])
  })

  it('sums weekly and monthly income together within each four-week group', () => {
    const grouped = crystalChartPeriods([
      { weekStart: '2026-10-01', totalIncome: 100, weeklyIncome: 60, monthlyIncome: 40, bossDetails: [] },
      { weekStart: '2026-10-08', totalIncome: 200, weeklyIncome: 150, monthlyIncome: 50, bossDetails: [] },
    ], '2026-10-01', 12)
    expect(grouped[0]).toMatchObject({ totalIncome: 300, weeklyIncome: 210, monthlyIncome: 90 })
    expect(grouped[1]).toMatchObject({ totalIncome: 0, weeklyIncome: 0, monthlyIncome: 0 })
  })

  it('anchors the window on the selected end date', () => {
    expect(crystalWindowRange('2026-09-30', 4)).toEqual({ dateFrom: '2026-09-03', dateTo: '2026-09-30' })
    expect(crystalWindowRange('2026-09-30', 12)).toEqual({ dateFrom: '2026-07-09', dateTo: '2026-09-30' })
  })

  it('rounds the chart axis up to a four-step nice maximum in 억', () => {
    expect(crystalAxisMax(940_000_000)).toBe(12)
    expect(crystalAxisMax(3_120_000_000)).toBe(40)
    expect(crystalAxisMax(0)).toBe(4)
  })
})
