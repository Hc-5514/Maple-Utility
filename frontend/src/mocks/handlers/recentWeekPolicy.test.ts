import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { setupServer } from 'msw/node'
import { schedulerHandlers } from './scheduler'

const server = setupServer(...schedulerHandlers)
const base = 'http://localhost/api/v1/scheduler/1'
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => { server.resetHandlers(); vi.useRealTimers() })
afterAll(() => server.close())

async function policy(week: string, month = '2026-10-01') {
  return (await (await fetch(`${base}/boss?weeklyDate=${week}&monthlyDate=${month}`)).json()).data
}

describe('최근 두 주차 목업 계약', () => {
  it('이번 주·지난주 잠금 및 지지난주 해제', async () => {
    vi.setSystemTime(new Date('2026-10-15T03:00:00+09:00'))
    expect((await policy('2026-10-15')).weeklyCustomizable).toBe(false)
    expect((await policy('2026-10-08')).weeklyCustomizable).toBe(false)
    expect((await policy('2026-10-01')).weeklyCustomizable).toBe(true)
  })

  it('월을 걸친 주차의 월간 잠금과 다음 목요일 해제', async () => {
    vi.setSystemTime(new Date('2026-10-01T03:00:00+09:00'))
    expect((await policy('2026-09-24', '2026-09-01')).monthlyCustomizable).toBe(false)
    vi.setSystemTime(new Date('2026-10-08T03:00:00+09:00'))
    expect((await policy('2026-10-01', '2026-09-01')).monthlyCustomizable).toBe(true)
    expect((await policy('2026-10-01')).monthlyCustomizable).toBe(false)
  })

  it('잠긴 기간의 직접 추가 요청 거절', async () => {
    vi.setSystemTime(new Date('2026-10-15T03:00:00+09:00'))
    const response = await fetch(`${base}/boss/selection`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ periodStart: '2026-10-08', resetPeriod: 'WEEKLY', bossIds: [5] }),
    })
    expect(response.status).toBe(400)
  })
})
