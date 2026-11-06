// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WeekDateButton from './WeekDateButton'

afterEach(cleanup)
const ending = {
  label: '종료일',
  value: '2026-11-11',
  weekday: 3 as const,
  min: '2026-11-05',
  max: '2026-11-11',
  rangeStart: '2026-11-05',
  rangeEnd: '2026-11-11',
}
const day = (date: string) => screen.getByRole<HTMLButtonElement>('button', { name: date })

describe('WeekDateButton', () => {
  it('marks the complete range but only permits Wednesdays within the bounds', () => {
    const onChange = vi.fn()
    render(<WeekDateButton {...ending} onChange={onChange} />)
    fireEvent.click(screen.getByRole('button', { name: '종료일 2026-11-11' }))
    expect(day('2026-11-04').disabled).toBe(true)
    expect(day('2026-11-05').disabled).toBe(true)
    expect(day('2026-11-11').disabled).toBe(false)
    expect(day('2026-11-18').disabled).toBe(true)
    for (let number = 5; number <= 11; number++)
      expect(day(`2026-11-${String(number).padStart(2, '0')}`).dataset.inRange).toBe('true')
    fireEvent.click(day('2026-11-06'))
    expect(onChange).not.toHaveBeenCalled()
    fireEvent.click(day('2026-11-11'))
    expect(onChange).toHaveBeenCalledWith('2026-11-11')
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '종료일 2026-11-11' }))
  })
  it('selects Thursdays only and supports arrow keyboard navigation across months', () => {
    render(
      <WeekDateButton
        label="시작일"
        value="2026-11-05"
        weekday={4}
        max="2026-11-11"
        onChange={vi.fn()}
      />
    )
    fireEvent.click(screen.getByRole('button', { name: '시작일 2026-11-05' }))
    expect(day('2026-11-06').disabled).toBe(true)
    expect(day('2026-11-12').disabled).toBe(true)
    fireEvent.keyDown(day('2026-11-05'), { key: 'ArrowLeft' })
    expect(document.activeElement).toBe(day('2026-10-29'))
    fireEvent.keyDown(day('2026-10-29'), { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
  })
  it('dismisses on outside click and focus leaving the popover', () => {
    render(
      <>
        <WeekDateButton {...ending} onChange={vi.fn()} />
        <button>외부</button>
      </>
    )
    fireEvent.click(screen.getByRole('button', { name: '종료일 2026-11-11' }))
    fireEvent.pointerDown(screen.getByText('외부'))
    expect(screen.queryByRole('dialog')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '종료일 2026-11-11' }))
    act(() => screen.getByText('외부').focus())
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
