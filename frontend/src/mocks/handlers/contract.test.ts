import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { bossHandlers } from './boss'
import { schedulerHandlers } from './scheduler'
import { characterHandlers } from './character'
import { statsHandlers } from './stats'
import { authHandlers } from './auth'
import { apiKeyHandlers } from './apiKey'
import { previewBossRecords, previewPartySize, previewWeekStart, resetPreviewPeriods } from '../fixtures/nexonPreview'
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
      const response = await fetch(`http://localhost/api/v1/scheduler/${characterId}/boss?weeklyDate=${previewWeekStart}&monthlyDate=2026-10-01`)
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

  it('applies an edited crystal price to the saved meso and the crystal stats', async () => {
    const bossId = previewBossRecords(1)[0].bossId
    const periodUrl = `http://localhost/api/v1/boss/${bossId}/period?characterId=1&periodStart=2026-10-01`
    const before = (await (await fetch(periodUrl)).json()).data
    const crystal = before.items.find((item: { dropItem: { itemKind: string } }) => item.dropItem.itemKind === 'CRYSTAL')
    const statsUrl = 'http://localhost/api/v1/stats/crystal?dateFrom=2026-10-01&dateTo=2026-10-07'
    const totalBefore = (await (await fetch(statsUrl)).json()).data.totalCrystalIncome

    const edited = before.crystalPrice * 2
    const response = await fetch(`http://localhost/api/v1/boss/${bossId}/period`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        characterId: 1, periodStart: '2026-10-01', partySize: before.partySize, crystalPrice: edited,
        items: [{ bossDropItemId: crystal.dropItem.id, acquired: true, quantity: 1 }],
      }),
    })
    const saved = (await response.json()).data
    const savedCrystal = saved.items.find((item: { dropItem: { itemKind: string } }) => item.dropItem.itemKind === 'CRYSTAL')
    expect(saved.crystalPrice).toBe(edited)
    expect(savedCrystal.mesoAmount).toBe(Math.floor(edited / before.partySize))
    const totalAfter = (await (await fetch(statsUrl)).json()).data.totalCrystalIncome
    expect(totalAfter).toBe(totalBefore - crystal.mesoAmount + savedCrystal.mesoAmount)
    expect((await fetch(`http://localhost/api/v1/boss/${bossId}/period`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ characterId: 1, periodStart: '2026-10-01', partySize: before.partySize, crystalPrice: -1, items: [] }),
    })).status).toBe(400)
  })

  it('stores a box detail item and shows it in the item history', async () => {
    let target: { bossId: number; boxId: number; boxName: string; options: string[]; partySize: number } | null = null
    for (const boss of previewBossRecords(1)) {
      const period = (await (await fetch(`http://localhost/api/v1/boss/${boss.bossId}/period?characterId=1&periodStart=2026-10-01`)).json()).data
      const box = period.items.find((item: { dropItem: { detailOptions: string[] } }) => item.dropItem.detailOptions.length > 0)
      if (box) {
        target = { bossId: boss.bossId ?? 0, boxId: box.dropItem.id, boxName: box.dropItem.itemName, options: box.dropItem.detailOptions, partySize: period.partySize }
        break
      }
    }
    expect(target).not.toBeNull()
    const { bossId, boxId, boxName, options, partySize } = target!
    const put = (detailItem: string) => fetch(`http://localhost/api/v1/boss/${bossId}/period`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ characterId: 1, periodStart: '2026-10-01', partySize, items: [{ bossDropItemId: boxId, acquired: true, quantity: 1, detailItem }] }),
    })

    const saved = (await (await put(options[1])).json()).data
    expect(saved.items.find((item: { dropItem: { id: number } }) => item.dropItem.id === boxId).detailItem).toBe(options[1])
    const history = (await (await fetch('http://localhost/api/v1/stats/boss-items?characterId=1&size=30')).json()).data
    expect(history.content.some((item: { itemName: string }) => item.itemName === `${boxName} (${options[1]})`)).toBe(true)

    const rejected = (await (await put('옵션에 없는 값')).json()).data
    expect(rejected.items.find((item: { dropItem: { id: number } }) => item.dropItem.id === boxId).detailItem).toBeNull()
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

  it('paginates forty simulated item acquisitions without crystals', async () => {
    const base = 'http://localhost/api/v1/stats/boss-items?dateFrom=2026-10-01&dateTo=2026-10-07'
    const first = (await (await fetch(`${base}&page=0&size=10`)).json()).data
    const last = (await (await fetch(`${base}&page=3&size=10`)).json()).data
    const twenty = (await (await fetch(`${base}&page=1&size=20`)).json()).data
    const thirty = (await (await fetch(`${base}&page=1&size=30`)).json()).data

    expect(first).toMatchObject({ totalElements: 40, totalPages: 4, page: 0, size: 10 })
    expect(first.content).toHaveLength(10)
    expect(last.content).toHaveLength(10)
    expect(twenty.content).toHaveLength(20)
    expect(thirty.content).toHaveLength(10)
    expect(first.content.every((item: { itemName: string }) => !item.itemName.includes('결정'))).toBe(true)
    expect((await (await fetch(`${base}&page=4&size=10`)).json()).data.content).toEqual([])
  })

  it('filters dummy history by character and date before counting pages', async () => {
    const base = 'http://localhost/api/v1/stats/boss-items?dateFrom=2026-10-01&dateTo=2026-10-07'
    const first = (await (await fetch(`${base}&characterId=1&size=10`)).json()).data
    const second = (await (await fetch(`${base}&characterId=2&size=10`)).json()).data
    const singleDate = (await (await fetch('http://localhost/api/v1/stats/boss-items?dateFrom=2026-10-07&dateTo=2026-10-07&characterId=1')).json()).data

    expect(first.totalElements).toBe(20)
    expect(second.totalElements).toBe(20)
    expect(first.content.every((item: { characterName: string }) => item.characterName === '꼬농')).toBe(true)
    expect(second.content.every((item: { characterName: string }) => item.characterName === '말랑꼬농')).toBe(true)
    expect(singleDate.totalElements).toBe(2)
    expect(singleDate.content.every((item: { acquiredDate: string }) => item.acquiredDate === '2026-10-07')).toBe(true)
    expect((await fetch(`${base}&size=11`)).status).toBe(400)
  })
})
