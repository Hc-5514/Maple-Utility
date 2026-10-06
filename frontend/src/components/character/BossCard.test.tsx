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
    expect(screen.getByText('✓ 처치')).toBeTruthy()
    expect(screen.queryByText('미처치')).toBeNull()
  })

  it('shows an undefeated boss', () => {
    render(<BossCard record={{ ...record, isCompleted: false }} onClickDetail={vi.fn()} />)
    expect(screen.getByText('미처치')).toBeTruthy()
  })
})
