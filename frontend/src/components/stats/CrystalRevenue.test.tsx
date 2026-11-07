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
    expect(row.className).toContain('grid-cols-1')
    expect(row.className).toContain('lg:grid-cols-[265px_minmax(0,1fr)]')
    expect(row.className).not.toContain('flex-wrap')
    expect(chart.className).toContain('w-full')
    expect(chart.className).toContain('lg:row-start-2')
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

  it.each([4, 12] as const)('positions each %i-week bar tooltip independently above the entire plot', (window) => {
    const chart = renderChart(window)
    const slot = screen.getByTestId('crystal-tooltip-slot')
    const plot = screen.getByTestId('crystal-plot')
    const rect = (left: number, width: number, top = 100, height = 204): DOMRect => ({
      left, right: left + width, top, bottom: top + height, width, height, x: left, y: top, toJSON: () => ({}),
    })
    vi.spyOn(slot, 'getBoundingClientRect').mockReturnValue(rect(40, 900, 100, 124))
    vi.spyOn(plot, 'getBoundingClientRect').mockReturnValue(rect(300, 640, 236))
    const bars = within(chart).getAllByRole('button')
    const positions: number[] = []
    for (const [index, left] of [300, 520, 800].entries()) {
      vi.spyOn(bars[index], 'getBoundingClientRect').mockReturnValue(rect(left, 100, 236))
      fireEvent.mouseEnter(bars[index])
      const tooltip = screen.getByRole('tooltip')
      positions.push(Number.parseFloat(tooltip.style.left))
      expect(tooltip.parentElement).toBe(slot)
      expect(tooltip.className).toContain('top-0')
      expect(tooltip.className).toContain('h-[124px]')
      expect(slot.getBoundingClientRect().bottom + 12).toBe(plot.getBoundingClientRect().top)
    }
    expect(positions).toEqual([187, 407, 654])
    // The last tooltip is clamped to the card's inner right edge, not the viewport.
    expect(positions[2] + 246).toBe(900)
  })

  it('keeps the slot reserved before, during and after hover with no chart resizing', () => {
    const chart = renderChart(4)
    const slot = screen.getByTestId('crystal-tooltip-slot')
    const plot = screen.getByTestId('crystal-plot')
    const slotClass = slot.className
    const plotClass = plot.className
    expect(slotClass).toContain('h-[124px]')
    expect(plotClass).toContain('h-[204px]')
    fireEvent.mouseEnter(within(chart).getAllByRole('button')[0])
    expect(screen.getByRole('tooltip').parentElement).toBe(slot)
    expect(slot.className).toBe(slotClass)
    expect(plot.className).toBe(plotClass)
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(slot.className).toBe(slotClass)
    expect(plot.className).toBe(plotClass)
    expect(screen.queryByRole('tooltip')).toBeNull()
  })

  it('clamps narrow mobile slots to the viewport while keeping the reserved height', () => {
    const originalWidth = globalThis.innerWidth
    Object.defineProperty(globalThis, 'innerWidth', { value: 320, configurable: true })
    const chart = renderChart(4)
    const slot = screen.getByTestId('crystal-tooltip-slot')
    vi.spyOn(slot, 'getBoundingClientRect').mockReturnValue({ left: 40, right: 280, width: 240 } as DOMRect)
    const bars = within(chart).getAllByRole('button')
    vi.spyOn(bars[0], 'getBoundingClientRect').mockReturnValue({ left: 0, right: 30 } as DOMRect)
    fireEvent.mouseEnter(bars[0])
    expect(Number.parseFloat(screen.getByRole('tooltip').style.left) + 40).toBe(8)
    vi.spyOn(bars[1], 'getBoundingClientRect').mockReturnValue({ left: 300, right: 320 } as DOMRect)
    fireEvent.mouseEnter(bars[1])
    expect(Number.parseFloat(screen.getByRole('tooltip').style.left) + 40 + 246).toBe(312)
    Object.defineProperty(globalThis, 'innerWidth', { value: originalWidth, configurable: true })
  })

  it('dismisses stale data immediately on period or window changes', () => {
    const { rerender } = render(<CrystalRevenue window={4} periods={periods} onWindowChange={vi.fn()} />)
    const bar = screen.getAllByRole('button')[2]
    fireEvent.click(bar)
    expect(screen.getByRole('tooltip')).toBeTruthy()
    rerender(<CrystalRevenue window={4} periods={periods.map((period) => ({ ...period }))} onWindowChange={vi.fn()} />)
    expect(screen.queryByRole('tooltip')).toBeNull()
    fireEvent.click(screen.getAllByRole('button')[2])
    expect(screen.getByRole('tooltip')).toBeTruthy()
    rerender(<CrystalRevenue window={12} periods={periods} onWindowChange={vi.fn()} />)
    expect(screen.queryByRole('tooltip')).toBeNull()
  })
})
