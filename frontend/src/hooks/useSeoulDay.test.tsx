// @vitest-environment jsdom
import { act, cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useSeoulDay } from './useSeoulDay'

function Day() { return <output>{useSeoulDay()}</output> }

afterEach(() => { cleanup(); vi.useRealTimers() })

describe('서울 기준 잠금 정보 갱신', () => {
  it('열린 화면에서 서울 목요일 자정에 날짜 변경', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-14T14:59:59Z'))
    render(<Day />)
    expect(screen.getByText('2026-10-14')).toBeTruthy()
    act(() => { vi.advanceTimersByTime(1000) })
    expect(screen.getByText('2026-10-15')).toBeTruthy()
  })

  it('절전 후 포커스 복귀 시 누락한 날짜 전환 반영', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-14T14:59:00Z'))
    render(<Day />)
    vi.setSystemTime(new Date('2026-10-14T15:10:00Z'))
    act(() => { window.dispatchEvent(new Event('focus')) })
    expect(screen.getByText('2026-10-15')).toBeTruthy()
  })
})
