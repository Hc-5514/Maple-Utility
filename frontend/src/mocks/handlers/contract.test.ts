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

  it('returns and saves one period snapshot', async () => {
    const url = 'http://localhost/api/v1/boss/2/period?characterId=1&periodStart=2026-10-08'
    const before = await (await fetch(url)).json()
    const crystal = before.data.items.find((item: { dropItem: { itemKind: string } }) => item.dropItem.itemKind === 'CRYSTAL')
    expect(crystal.acquired).toBe(false)

    const response = await fetch('http://localhost/api/v1/boss/2/period', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        characterId: 1, periodStart: '2026-10-08', partySize: 2,
        items: [{ bossDropItemId: crystal.dropItem.id, acquired: true, quantity: 1, mesoAmount: 24_450_000 }],
      }),
    })
    const saved = await response.json()

    expect(response.status).toBe(200)
    expect(saved.data.partySize).toBe(2)
    expect(saved.data.items.find((item: { dropItem: { id: number } }) => item.dropItem.id === crystal.dropItem.id).mesoAmount)
      .toBe(24_450_000)
  })
})
