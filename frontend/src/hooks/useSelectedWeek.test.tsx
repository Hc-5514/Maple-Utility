// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useSelectedWeek } from './useSelectedWeek'
import { useWeekStore } from '../stores/weekStore'

const at = (url: string) => ({ children }: { children: ReactNode }) => (
  <MemoryRouter initialEntries={[url]}>{children}</MemoryRouter>
)

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-12T03:00:00.000Z')) // 2026-10-12 12:00 Seoul
  useWeekStore.setState({ week: null })
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

describe('useSelectedWeek', () => {
  it('defaults to the current Thursday week', () => {
    const { result } = renderHook(() => useSelectedWeek(), { wrapper: at('/dashboard') })
    expect(result.current.week).toEqual({ start: '2026-10-08', end: '2026-10-14', date: '2026-10-12' })
    expect(result.current.search).toBe('')
  })

  it('reads ?week from the URL and shares it through the store', () => {
    const { result } = renderHook(() => useSelectedWeek(), { wrapper: at('/dashboard/1?week=2026-09-24') })
    expect(result.current.week.start).toBe('2026-09-24')
    expect(result.current.search).toBe('?week=2026-09-24')
    expect(useWeekStore.getState().week).toBe('2026-09-24')
  })

  it('falls back to the shared week when the URL has none', () => {
    useWeekStore.setState({ week: '2026-10-01' })
    const { result } = renderHook(() => useSelectedWeek(), { wrapper: at('/dashboard') })
    expect(result.current.week.date).toBe('2026-10-07')
  })

  it('replaces weeks outside the recent twelve with the current week', () => {
    const { result } = renderHook(() => useSelectedWeek(), { wrapper: at('/dashboard?week=2025-01-02') })
    expect(result.current.week.start).toBe('2026-10-08')
  })

  it('updates the URL and store on selection', () => {
    const { result } = renderHook(() => useSelectedWeek(), { wrapper: at('/dashboard') })
    act(() => result.current.setWeek('2026-09-17'))
    expect(result.current.week.start).toBe('2026-09-17')
    expect(result.current.search).toBe('?week=2026-09-17')
    expect(useWeekStore.getState().week).toBe('2026-09-17')
  })
})
