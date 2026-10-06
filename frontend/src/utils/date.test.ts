import { describe, expect, it } from 'vitest'
import { localDate, thursdayWeekStart } from './date'

describe('local scheduler dates', () => {
  it('uses the Thursday of the current week across the reset boundary', () => {
    expect(thursdayWeekStart(new Date(2026, 8, 30, 23, 30))).toBe('2026-09-24')
    expect(thursdayWeekStart(new Date(2026, 9, 1, 0, 30))).toBe('2026-10-01')
    expect(thursdayWeekStart(new Date(2026, 9, 4, 12, 0))).toBe('2026-10-01')
  })

  it('keeps the browser calendar date at midnight', () => {
    expect(localDate(new Date(2026, 9, 1, 0, 30))).toBe('2026-10-01')
  })
})
