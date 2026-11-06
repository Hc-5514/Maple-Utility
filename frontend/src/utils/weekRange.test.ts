import { describe, expect, it } from 'vitest'
import { currentWeekRange } from './weekRange'

describe('currentWeekRange', () => {
  it('includes the future Wednesday in the current week', () => {
    expect(currentWeekRange('2026-11-07')).toEqual({ dateFrom: '2026-11-05', dateTo: '2026-11-11' })
  })
  it('keeps Thursday and Wednesday within their own week', () => {
    for (const today of ['2026-11-05', '2026-11-11'])
      expect(currentWeekRange(today)).toEqual({ dateFrom: '2026-11-05', dateTo: '2026-11-11' })
  })
  it('handles year and leap month boundaries', () => {
    expect(currentWeekRange('2027-01-01')).toEqual({ dateFrom: '2026-12-31', dateTo: '2027-01-06' })
    expect(currentWeekRange('2024-02-29')).toEqual({ dateFrom: '2024-02-29', dateTo: '2024-03-06' })
  })
})
