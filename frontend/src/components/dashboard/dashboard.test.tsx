// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import WorldSelectionModal from './WorldSelectionModal'
import WeeklyPeriodSelect, { recentWeeks } from './WeeklyPeriodSelect'
import CharacterSummaryCard from './CharacterSummaryCard'
import type { CharacterSchedulerSummary } from '../../types'

afterEach(cleanup)
describe('dashboard interactions', () => {
  it('creates twelve Thursday to Wednesday weeks with current date and past closing dates', () => {
    const weeks = recentWeeks(new Date('2026-10-14T12:00:00'))
    expect(weeks).toHaveLength(12)
    expect(weeks[0]).toEqual({ start: '2026-10-08', end: '2026-10-14', date: '2026-10-14' })
    expect(weeks[1]).toEqual({ start: '2026-10-01', end: '2026-10-07', date: '2026-10-07' })
    expect(recentWeeks(new Date('2026-10-15T12:00:00'))[0].start).toBe('2026-10-15')
  })
  it('starts empty, selects multiple worlds, and commits only after confirmation', async () => {
    const save = vi.fn().mockResolvedValue(undefined),
      close = vi.fn()
    render(<WorldSelectionModal selectedWorlds={[]} onClose={close} onSave={save} />)
    expect(
      screen.getAllByRole('checkbox').every((input) => !(input as HTMLInputElement).checked)
    ).toBe(true)
    fireEvent.click(screen.getByRole('checkbox', { name: '루나' }))
    fireEvent.click(screen.getByRole('checkbox', { name: '엘리시움' }))
    expect(save).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText('선택 완료'))
    await waitFor(() => expect(save).toHaveBeenCalledWith(['루나', '엘리시움']))
    await waitFor(() => expect(close).toHaveBeenCalledOnce())
  })
  it('resets and cancels without saving; traps focus and handles Escape', () => {
    const save = vi.fn(),
      close = vi.fn()
    render(<WorldSelectionModal selectedWorlds={['루나']} onClose={close} onSave={save} />)
    fireEvent.click(screen.getByText('전체 월드 선택'))
    expect(
      screen.getAllByRole('checkbox').every((input) => (input as HTMLInputElement).checked)
    ).toBe(true)
    fireEvent.click(screen.getByText('초기화'))
    expect(
      screen.getAllByRole('checkbox').every((input) => !(input as HTMLInputElement).checked)
    ).toBe(true)
    screen.getByRole('button', { name: '선택 완료' }).focus()
    fireEvent.keyDown(window, { key: 'Tab' })
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '닫기' }))
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(close).toHaveBeenCalledOnce()
    expect(save).not.toHaveBeenCalled()
  })
  it('retains selection after saving fails and allows retry', async () => {
    const save = vi
        .fn()
        .mockRejectedValueOnce(new Error('failed'))
        .mockResolvedValueOnce(undefined),
      close = vi.fn()
    render(<WorldSelectionModal selectedWorlds={['루나']} onClose={close} onSave={save} />)
    fireEvent.click(screen.getByText('선택 완료'))
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(close).not.toHaveBeenCalled()
    expect((screen.getByRole('checkbox', { name: '루나' }) as HTMLInputElement).checked).toBe(true)
    fireEvent.click(screen.getByText('선택 완료'))
    await waitFor(() => expect(close).toHaveBeenCalledOnce())
  })
  it('changes the summary query date by the selected period and closes the dropdown', () => {
    const change = vi.fn(),
      weeks = recentWeeks()
    render(<WeeklyPeriodSelect value={weeks[0].date} onChange={change} />)
    fireEvent.click(screen.getByText('주간 보스 기간'))
    fireEvent.click(
      screen.getByText(weeks[1].start.replace(/-/g, '.') + ' ~ ' + weeks[1].end.replace(/-/g, '.'))
    )
    expect(change).toHaveBeenCalledWith(weeks[1].date)
    expect(
      screen.queryByText(
        weeks[1].start.replace(/-/g, '.') + ' ~ ' + weeks[1].end.replace(/-/g, '.')
      )
    ).toBeNull()
  })
  it('distinguishes missing records from non-target contents and preserves the detail route', () => {
    const character: CharacterSchedulerSummary = {
      characterId: 1,
      characterName: '테스트',
      characterLevel: 200,
      characterClass: '비숍',
      characterImage: null,
      worldName: '루나',
      hasRecords: false,
      daily: { completed: 0, total: 0 },
      weekly: { completed: 0, total: 0 },
      weeklyBoss: { completed: 0, total: 0 },
      monthlyBoss: { completed: 0, total: 0 },
    }
    const { rerender } = render(
      <MemoryRouter>
        <CharacterSummaryCard character={character} />
      </MemoryRouter>
    )
    expect(screen.getByText('기록 없음')).toBeTruthy()
    expect(screen.getByRole('link', { name: '상세 보기' }).getAttribute('href')).toBe(
      '/dashboard/1'
    )
    rerender(
      <MemoryRouter>
        <CharacterSummaryCard character={{ ...character, hasRecords: true }} />
      </MemoryRouter>
    )
    expect(screen.queryByText('기록 없음')).toBeNull()
    expect(screen.getAllByText('비대상')).toHaveLength(4)
  })
})
