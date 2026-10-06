import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { bossHandlers } from './boss'
import { schedulerHandlers } from './scheduler'

const server = setupServer(...bossHandlers, ...schedulerHandlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('backend response fixtures', () => {
  it('returns isCompleted for the boss detail response', async () => {
    const response = await fetch('http://localhost/api/v1/scheduler/1/boss?date=2026-07-13')
    const body = await response.json()
    const defeated = body.data.weeklyBosses.find((boss: { bossId: number }) => boss.bossId === 5)

    expect(response.status).toBe(200)
    expect(defeated.isCompleted).toBe(true)
    expect(defeated).not.toHaveProperty('completed')
  })

  it('returns boss-specific drop item status instead of a flat acquisition list', async () => {
    const response = await fetch('http://localhost/api/v1/boss/4/drop-items/acquisitions?characterId=1')
    const body = await response.json()
    const acquired = body.data.find((status: { dropItem: { id: number } }) => status.dropItem.id === 8)

    expect(response.status).toBe(200)
    expect(acquired.acquired).toBe(true)
    expect(acquired.acquisitions[0].bossDropItemId).toBe(8)
    expect(acquired.acquisitions[0]).not.toHaveProperty('createdAt')
  })
})
