import { describe, expect, it } from 'vitest'
import { formatDotDate, formatDuration, formatKoreanAmount } from './format'

describe('hunting format', () => {
  it('formats meso with 억·만 units', () => {
    expect(formatKoreanAmount(4_325_000_000)).toBe('43억 2500만')
    expect(formatKoreanAmount(300_000_000)).toBe('3억')
    expect(formatKoreanAmount(52_000)).toBe('5만')
    expect(formatKoreanAmount(9_999)).toBe('')
  })

  it('formats play duration', () => {
    expect(formatDuration(150)).toBe('2시간 30분')
    expect(formatDuration(120)).toBe('2시간')
    expect(formatDuration(45)).toBe('45분')
    expect(formatDuration(null)).toBe('-')
  })

  it('formats dates with dots', () => {
    expect(formatDotDate('2026-09-01')).toBe('2026. 09. 01')
  })
})
