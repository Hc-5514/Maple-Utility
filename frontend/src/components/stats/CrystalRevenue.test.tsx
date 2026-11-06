// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CrystalRevenue from './CrystalRevenue'

afterEach(cleanup)

const periods = [
  { weekStart: '2026-09-10 ~ 2026-09-16', weeklyIncome: 700_000_000, monthlyIncome: 300_000_000, totalIncome: 1_000_000_000 },
  { weekStart: '2026-09-17 ~ 2026-09-23', weeklyIncome: 2_000_000_000, monthlyIncome: 0, totalIncome: 2_000_000_000 },
  { weekStart: '2026-09-24 ~ 2026-09-30', weeklyIncome: 3_000_000_000, monthlyIncome: 0, totalIncome: 3_000_000_000 },
  { weekStart: '2026-10-01 ~ 2026-10-07', weeklyIncome: 4_000_000_000, monthlyIncome: 0, totalIncome: 4_000_000_000 },
]

function renderChart(window: 4 | 12) {
  render(<CrystalRevenue window={window} periods={periods} onWindowChange={vi.fn()} />)
  return screen.getByRole('group', { name: `최근 ${window}주 결정석 수익 막대 차트` })
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
    const bars = four.querySelectorAll('button > span')
    expect(bars).toHaveLength(4)
    bars.forEach((bar) => {
      expect(bar.className).toContain('w-full')
      expect(bar.className).toContain('max-w-[150px]')
      expect(bar.className).not.toMatch(/(^|\s)w-\[(150|200)px\]/)
    })
    cleanup()
    const twelve = renderChart(12)
    twelve.querySelectorAll('button > span').forEach((bar) => {
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


describe('CrystalRevenue tooltip', () => {
  it('shows exact total, weekly and monthly amounts on hover and dismisses on mouse leave', () => {
    const chart = renderChart(4)
    const bar = within(chart).getAllByRole('button')[0]
    fireEvent.mouseEnter(bar)
    const tooltip = screen.getByRole('tooltip')
    expect(within(tooltip).getByText('총합')).toBeTruthy()
    expect(within(tooltip).getByText('주간 결정')).toBeTruthy()
    expect(within(tooltip).getByText('월간 결정')).toBeTruthy()
    expect(within(tooltip).getByText('1,000,000,000')).toBeTruthy()
    expect(within(tooltip).getByText('700,000,000')).toBeTruthy()
    expect(within(tooltip).getByText('300,000,000')).toBeTruthy()
    expect(within(tooltip).getAllByText('메소')).toHaveLength(3)
    fireEvent.mouseLeave(bar)
    expect(screen.queryByRole('tooltip')).toBeNull()
  })

  it('shows the focused bar, supports Escape, and changes the tooltip on another focus', () => {
    const chart = renderChart(4)
    const bars = within(chart).getAllByRole('button')
    fireEvent.focus(bars[0])
    expect(bars[0].getAttribute('aria-describedby')).toBe(screen.getByRole('tooltip').id)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('tooltip')).toBeNull()
    fireEvent.focus(bars[1])
    expect(within(screen.getByRole('tooltip')).getByText(periods[1].weekStart)).toBeTruthy()
    fireEvent.blur(bars[1])
    expect(screen.queryByRole('tooltip')).toBeNull()
  })

  it('opens on a tap/click and dismisses outside or when the window resizes', () => {
    const chart = renderChart(12)
    const bar = within(chart).getAllByRole('button')[0]
    fireEvent.click(bar)
    expect(screen.getByRole('tooltip')).toBeTruthy()
    fireEvent.pointerDown(document.body)
    expect(screen.queryByRole('tooltip')).toBeNull()
    fireEvent.click(bar)
    fireEvent.resize(window)
    expect(screen.queryByRole('tooltip')).toBeNull()
  })

  it('keeps zero-income periods interactive and displays up to 1000억 without truncation', () => {
    render(<CrystalRevenue window={4} periods={[
      { weekStart: '2026-11-05 ~ 2026-11-11', totalIncome: 0, weeklyIncome: 0, monthlyIncome: 0 },
      { weekStart: '2026-11-12 ~ 2026-11-18', totalIncome: 100_000_000_000, weeklyIncome: 90_000_000_000, monthlyIncome: 10_000_000_000 },
    ]} onWindowChange={vi.fn()} />)
    const bars = within(screen.getByRole('group', { name: '최근 4주 결정석 수익 막대 차트' })).getAllByRole('button')
    fireEvent.click(bars[0])
    expect(within(screen.getByRole('tooltip')).getAllByText('0')).toHaveLength(3)
    fireEvent.click(bars[1])
    const tooltip = screen.getByRole('tooltip')
    expect(within(tooltip).getByText('100,000,000,000')).toBeTruthy()
    expect(tooltip.className).not.toContain('truncate')
  })

  it('clamps the tooltip within the viewport at the left and right edges', () => {
    const chart = renderChart(4)
    const bars = within(chart).getAllByRole('button')
    const rect = { top: 200, right: 2000, left: 1980, bottom: 300, width: 20, height: 100, x: 1980, y: 200, toJSON: () => ({}) }
    vi.spyOn(bars[0].querySelector('span')!, 'getBoundingClientRect').mockReturnValue(rect)
    fireEvent.mouseEnter(bars[0])
    expect(Number.parseFloat(screen.getByRole('tooltip').style.left)).toBeLessThanOrEqual(window.innerWidth - 254)
    fireEvent.mouseEnter(bars[1])
    expect(screen.getByRole('tooltip').style.left).toBe('8px')
  })
})
