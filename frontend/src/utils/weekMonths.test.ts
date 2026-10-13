import { describe, expect, it } from 'vitest'
import { monthLabel, weekMonths } from './date'

describe('weekMonths', () => {
  it('returns one month for a week inside a month', () => {
    expect(weekMonths('2026-07-23', '2026-10-13')).toEqual(['2026-07-01'])
  })

  it('returns both months for a week crossing a month boundary', () => {
    expect(weekMonths('2026-07-30', '2026-10-13')).toEqual(['2026-07-01', '2026-08-01'])
  })

  it('omits months after the current month', () => {
    expect(weekMonths('2026-10-29', '2026-10-30')).toEqual(['2026-10-01'])
  })

  it('formats a month label', () => {
    expect(monthLabel('2026-08-01')).toBe('8월')
  })
})
