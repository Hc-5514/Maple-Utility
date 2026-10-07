import { http, HttpResponse } from 'msw'
import bossesFixture from '../fixtures/bosses.json'
import type { BossPeriod, DropRateTier } from '../../types'

const periods = new Map<string, BossPeriod>()

function periodKey(characterId: number, bossId: number, periodStart: string) {
  return `${characterId}:${bossId}:${periodStart}`
}

function initialPeriod(characterId: number, bossId: number, periodStart: string): BossPeriod {
  const boss = bossesFixture.masters.find((entry) => entry.id === bossId)
  const crystalPrice = boss?.crystalPrice ?? 0
  return {
    bossId, characterId, periodStart, partySize: 1, crystalPrice, savedAt: null,
    items: bossesFixture.dropItems
      .filter((item) => item.bossId === bossId && item.itemName !== '솔 에르다 조각')
      .map((item) => ({
        dropItem: {
          ...item,
          dropRateTier: item.dropRateTier as DropRateTier | null,
          itemKind: item.itemName === '강렬한 힘의 결정' ? 'CRYSTAL' as const : 'RANDOM' as const,
          defaultQuantity: 1,
        },
        acquired: false,
        quantity: 1,
        mesoAmount: item.itemName === '강렬한 힘의 결정' && crystalPrice > 0 ? crystalPrice : null,
      })),
  }
}

const acquisitions = [
  { id: 1, characterId: 1, bossDropItemId: 4,  acquiredDate: '2026-07-13', memo: null },
  { id: 2, characterId: 1, bossDropItemId: 8,  acquiredDate: '2026-07-13', memo: null },
  { id: 3, characterId: 1, bossDropItemId: 32, acquiredDate: '2026-07-14', memo: '더스크 하드' },
]
let nextAcqId = 4

export const bossHandlers = [
  http.get('*/api/v1/boss/:bossId/period', ({ params, request }) => {
    const url = new URL(request.url)
    const bossId = Number(params.bossId)
    const characterId = Number(url.searchParams.get('characterId'))
    const periodStart = url.searchParams.get('periodStart') ?? ''
    const key = periodKey(characterId, bossId, periodStart)
    return HttpResponse.json({ success: true, data: periods.get(key) ?? initialPeriod(characterId, bossId, periodStart) })
  }),

  http.put('*/api/v1/boss/:bossId/period', async ({ params, request }) => {
    const bossId = Number(params.bossId)
    const body = await request.json() as {
      characterId: number
      periodStart: string
      partySize: number
      items: { bossDropItemId: number; acquired: boolean; quantity: number; mesoAmount: number | null }[]
    }
    if (body.partySize < 1 || body.partySize > 6) {
      return HttpResponse.json({ success: false }, { status: 400 })
    }
    const key = periodKey(body.characterId, bossId, body.periodStart)
    const previous = periods.get(key) ?? initialPeriod(body.characterId, bossId, body.periodStart)
    const changed = new Map(body.items.map((item) => [item.bossDropItemId, item]))
    const saved: BossPeriod = {
      ...previous,
      partySize: body.partySize,
      savedAt: new Date().toISOString(),
      items: previous.items.map((item) => {
        const edit = changed.get(item.dropItem.id)
        return edit ? { ...item, acquired: edit.acquired, quantity: edit.quantity, mesoAmount: edit.mesoAmount } : item
      }),
    }
    periods.set(key, saved)
    return HttpResponse.json({ success: true, data: saved })
  }),

  http.get('*/api/v1/boss/:bossId/drop-items', ({ params }) => {
    const items = bossesFixture.dropItems.filter(i => i.bossId === Number(params.bossId))
    return HttpResponse.json({ success: true, data: items })
  }),

  http.get('*/api/v1/boss/:bossId/drop-items/acquisitions', ({ params, request }) => {
    const url = new URL(request.url)
    const characterId = Number(url.searchParams.get('characterId'))
    const items = bossesFixture.dropItems.filter(i => i.bossId === Number(params.bossId))
    return HttpResponse.json({ success: true, data: items.map((dropItem) => {
      const itemAcquisitions = acquisitions
        .filter(a => a.characterId === characterId && a.bossDropItemId === dropItem.id)
        .sort((a, b) => b.acquiredDate.localeCompare(a.acquiredDate) || b.id - a.id)
      return { dropItem, acquired: itemAcquisitions.length > 0, acquisitions: itemAcquisitions }
    }) })
  }),

  http.post('/api/v1/boss/item-acquisition', async ({ request }) => {
    const body = await request.json() as {
      characterId: number
      bossDropItemId: number
      acquiredDate: string
      memo?: string
    }
    const newAcq = {
      id: nextAcqId++,
      characterId: body.characterId,
      bossDropItemId: body.bossDropItemId,
      acquiredDate: body.acquiredDate,
      memo: body.memo ?? null,
    }
    acquisitions.push(newAcq)
    return HttpResponse.json({ success: true, data: newAcq }, { status: 201 })
  }),

  http.delete('/api/v1/boss/item-acquisition/:id', ({ params }) => {
    const idx = acquisitions.findIndex(a => a.id === Number(params.id))
    if (idx === -1) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    acquisitions.splice(idx, 1)
    return new HttpResponse(null, { status: 204 })
  }),
]
