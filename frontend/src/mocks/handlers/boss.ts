import { http, HttpResponse } from 'msw'
import bossesFixture from '../fixtures/bosses.json'

const acquisitions = [
  { id: 1, characterId: 1, bossDropItemId: 4,  acquiredDate: '2026-07-13', memo: null },
  { id: 2, characterId: 1, bossDropItemId: 8,  acquiredDate: '2026-07-13', memo: null },
  { id: 3, characterId: 1, bossDropItemId: 32, acquiredDate: '2026-07-14', memo: '더스크 하드' },
]
let nextAcqId = 4

export const bossHandlers = [
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
