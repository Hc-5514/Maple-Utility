// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import BossCard from './BossCard'
import type { SchedulerBossRecord } from '../../types'

const record: SchedulerBossRecord = {
  characterId: 1,
  bossId: 5,
  bossName: '데미안',
  difficulty: 'HARD',
  resetPeriod: 'WEEKLY',
  isCompleted: true,
  syncedAt: '2026-10-07T01:00:00',
}

afterEach(cleanup)

describe('BossCard', () => {
  it('shows a defeated boss from the backend isCompleted field', () => {
    render(<BossCard record={record} onClickDetail={vi.fn()} />)
    expect(screen.getByText('처치')).toBeTruthy()
    expect(screen.queryByText('미처치')).toBeNull()
  })

  it('shows an undefeated boss', () => {
    render(<BossCard record={{ ...record, isCompleted: false }} onClickDetail={vi.fn()} />)
    expect(screen.getByText('미처치')).toBeTruthy()
  })

  it('opens drop items only for a defeated boss', () => {
    const onClickDetail = vi.fn()
    render(<BossCard record={record} onClickDetail={onClickDetail} />)
    screen.getByRole('button', { name: '데미안 드랍 아이템' }).click()
    expect(onClickDetail).toHaveBeenCalledOnce()
  })

  it('ignores clicks on an undefeated boss', () => {
    const onClickDetail = vi.fn()
    render(<BossCard record={{ ...record, isCompleted: false }} onClickDetail={onClickDetail} />)
    expect(screen.queryByRole('button', { name: '데미안 드랍 아이템' })).toBeNull()
    screen.getByText('데미안').click()
    expect(onClickDetail).not.toHaveBeenCalled()
  })

  it('shows the Figma difficulty label and a separate delete action', () => {
    const onDelete = vi.fn()
    const onClickDetail = vi.fn()
    render(<BossCard record={record} onClickDetail={onClickDetail} onDelete={onDelete} />)
    expect(screen.getByText('HARD')).toBeTruthy()
    screen.getByRole('button', { name: '데미안 삭제' }).click()
    expect(onDelete).toHaveBeenCalledOnce()
    expect(onClickDetail).not.toHaveBeenCalled()
  })

  it('shows the backend-calculated crystal meso for a defeated boss', () => {
    render(<BossCard record={{ ...record, crystalPrice: 900_000_000, acquiredCrystalMeso: 300_000_000 }} onClickDetail={vi.fn()} />)
    expect(screen.getByText('300,000,000 메소')).toBeTruthy()
  })

  it('shows 0 meso for an undefeated boss', () => {
    render(<BossCard record={{ ...record, isCompleted: false, crystalPrice: 900_000_000, acquiredCrystalMeso: 0 }} onClickDetail={vi.fn()} />)
    expect(screen.getByText('0 메소')).toBeTruthy()
  })
})
