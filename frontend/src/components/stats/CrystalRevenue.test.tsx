// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CrystalRevenue from './CrystalRevenue'

afterEach(cleanup)

const periods = [
  { weekStart: '2026-09-10 ~ 2026-09-16', totalIncome: 1_000_000_000 },
  { weekStart: '2026-09-17 ~ 2026-09-23', totalIncome: 2_000_000_000 },
  { weekStart: '2026-09-24 ~ 2026-09-30', totalIncome: 3_000_000_000 },
  { weekStart: '2026-10-01 ~ 2026-10-07', totalIncome: 4_000_000_000 },
]

function renderChart(window: 4 | 12) {
  render(<CrystalRevenue window={window} periods={periods} onWindowChange={vi.fn()} />)
  return screen.getByRole('img', { name: `최근 ${window}주 결정석 수익 막대 차트` })
}

describe('CrystalRevenue responsive layout', () => {
  it('stacks the summary above the chart below lg and puts them side by side from lg', () => {
    const chart = renderChart(4)
    const row = chart.parentElement as HTMLElement
    expect(row.className).toContain('flex-col')
    expect(row.className).toContain('lg:flex-row')
    expect(row.className).not.toContain('flex-wrap')
    expect(chart.className).toContain('w-full')
    expect(chart.className).toContain('lg:flex-1')
  })

  it('shrinks bars with the container and caps their width at 150px (4 weeks) or 200px (12 weeks)', () => {
    const four = renderChart(4)
    const bars = four.querySelectorAll('[title*="억 메소"]')
    expect(bars).toHaveLength(4)
    bars.forEach((bar) => {
      expect(bar.className).toContain('w-full')
      expect(bar.className).toContain('max-w-[150px]')
      expect(bar.className).not.toMatch(/(^|\s)w-\[(150|200)px\]/)
    })
    cleanup()
    const twelve = renderChart(12)
    twelve.querySelectorAll('[title*="억 메소"]').forEach((bar) => {
      expect(bar.className).toContain('max-w-[200px]')
    })
  })

  it('lets the period labels wrap instead of overlapping', () => {
    const chart = renderChart(4)
    for (const period of periods) {
      const label = screen.getByText(period.weekStart)
      expect(chart.contains(label)).toBe(true)
      expect(label.className).not.toContain('whitespace-nowrap')
      expect(label.className).toContain('break-keep')
    }
  })
})
