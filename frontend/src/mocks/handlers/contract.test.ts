import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { bossHandlers } from './boss'
import { schedulerHandlers } from './scheduler'
import { characterHandlers } from './character'
import { statsHandlers } from './stats'
import { authHandlers } from './auth'
import { apiKeyHandlers } from './apiKey'
import { previewBossRecords, previewDate, previewPartySize, resetPreviewPeriods } from '../fixtures/nexonPreview'
import previewFixture from '../fixtures/nexonPreview.json'

const server = setupServer(
  ...authHandlers, ...apiKeyHandlers, ...bossHandlers,
  ...schedulerHandlers, ...characterHandlers, ...statsHandlers,
)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  resetPreviewPeriods()
})
afterAll(() => server.close())

describe('Nexon preview contracts', () => {
  it('loads authentication, API-key status, and the two-character dashboard', async () => {
    const [me, key, summary] = await Promise.all([
      fetch('http://localhost/api/v1/auth/me').then((response) => response.json()),
      fetch('http://localhost/api/v1/api-key/status').then((response) => response.json()),
      fetch('http://localhost/api/v1/scheduler/summary').then((response) => response.json()),
    ])

    expect(me.data.nickname).toBe('미리보기')
    expect(key.data.registered).toBe(true)
    expect(summary.data.characters.map((character: { characterName: string }) => character.characterName))
      .toEqual(['꼬농', '말랑꼬농'])
    expect(summary.data.characters.map((character: { weeklyBoss: { completed: number; total: number } }) => character.weeklyBoss))
      .toEqual([{ completed: 12, total: 12 }, { completed: 12, total: 12 }])
  })

  it('uses the two real character snapshots without credentials or OCIDs', async () => {
    const response = await fetch('http://localhost/api/v1/characters/favorites')
    const body = await response.json()

    expect(body.data.map((character: { characterName: string }) => character.characterName))
      .toEqual(['꼬농', '말랑꼬농'])
    expect(JSON.stringify(previewFixture)).not.toMatch(/live_|ocid|account_id/)
  })

  it('shows each registered weekly and monthly boss with actual completion flags', async () => {
    for (const characterId of [1, 2]) {
      const response = await fetch(`http://localhost/api/v1/scheduler/${characterId}/boss?date=${previewDate}`)
      const body = await response.json()
      expect(body.data.weeklyBosses).toHaveLength(12)
      expect(body.data.monthlyBosses).toHaveLength(1)
      expect(body.data.weeklyBosses.every((boss: { isCompleted: boolean }) => boss.isCompleted)).toBe(true)
      expect(body.data.monthlyBosses[0].isCompleted).toBe(false)
    }
  })

  it('assigns stable one-to-three-person parties and updates preview crystal stats after save', async () => {
    const bosses = previewBossRecords(1)
    expect(new Set(bosses.map((boss) => previewPartySize(1, boss.bossId ?? 0)))).toEqual(new Set([1, 2, 3]))
    const bossId = bosses[0].bossId
    const periodUrl = `http://localhost/api/v1/boss/${bossId}/period?characterId=1&periodStart=2026-10-01`
    const before = await (await fetch(periodUrl)).json()
    const crystal = before.data.items.find((item: { dropItem: { itemKind: string } }) => item.dropItem.itemKind === 'CRYSTAL')
    expect(crystal.acquired).toBe(true)
    expect(crystal.mesoAmount).toBe(Math.floor(before.data.crystalPrice / before.data.partySize))

    const statsUrl = 'http://localhost/api/v1/stats/crystal?dateFrom=2026-10-01&dateTo=2026-10-07'
    const totalBefore = (await (await fetch(statsUrl)).json()).data.totalCrystalIncome
    const response = await fetch(`http://localhost/api/v1/boss/${bossId}/period`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        characterId: 1, periodStart: '2026-10-01', partySize: before.data.partySize,
        items: [{ bossDropItemId: crystal.dropItem.id, acquired: false, quantity: 1, mesoAmount: crystal.mesoAmount }],
      }),
    })
    expect(response.status).toBe(200)
    const totalAfter = (await (await fetch(statsUrl)).json()).data.totalCrystalIncome
    expect(totalAfter).toBe(totalBefore - crystal.mesoAmount)
  })

  it('separates each character crystal total', async () => {
    const base = 'http://localhost/api/v1/stats/crystal?dateFrom=2026-10-01&dateTo=2026-10-07'
    const all = (await (await fetch(base)).json()).data.totalCrystalIncome
    const first = (await (await fetch(`${base}&characterId=1`)).json()).data.totalCrystalIncome
    const second = (await (await fetch(`${base}&characterId=2`)).json()).data.totalCrystalIncome

    expect(first).toBeGreaterThan(0)
    expect(second).toBeGreaterThan(0)
    expect(all).toBe(first + second)
  })
})
