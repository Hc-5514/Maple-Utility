import { http, HttpResponse } from 'msw'
import bossesFixture from '../fixtures/bosses.json'

const acquisitions = [
  { id: 1, characterId: 1, bossDropItemId: 4,  acquiredDate: '2026-07-13', memo: null, createdAt: '2026-07-13T22:00:00' },
  { id: 2, characterId: 1, bossDropItemId: 8,  acquiredDate: '2026-07-13', memo: null, createdAt: '2026-07-13T22:30:00' },
  { id: 3, characterId: 1, bossDropItemId: 32, acquiredDate: '2026-07-14', memo: '더스크 하드', createdAt: '2026-07-14T09:30:00' },
]

export const bossHandlers = [
  http.get('/api/v1/bosses', ({ request }) => {
    const url = new URL(request.url)
    const resetPeriod = url.searchParams.get('resetPeriod')
    const masters = resetPeriod
      ? bossesFixture.masters.filter(b => b.resetPeriod === resetPeriod)
      : bossesFixture.masters
    return HttpResponse.json({ success: true, data: masters })
  }),

  http.get('/api/v1/bosses/:id', ({ params }) => {
    const boss = bossesFixture.masters.find(b => b.id === Number(params.id))
    if (!boss) {
      return HttpResponse.json({ success: false, message: '보스를 찾을 수 없음' }, { status: 404 })
    }
    return HttpResponse.json({ success: true, data: boss })
  }),

  http.get('/api/v1/bosses/:id/drop-items', ({ params }) => {
    const items = bossesFixture.dropItems.filter(i => i.bossId === Number(params.id))
    return HttpResponse.json({ success: true, data: items })
  }),

  http.get('/api/v1/boss-acquisitions', ({ request }) => {
    const url = new URL(request.url)
    const characterId = Number(url.searchParams.get('characterId'))
    const filtered = characterId
      ? acquisitions.filter(a => a.characterId === characterId)
      : acquisitions
    return HttpResponse.json({ success: true, data: filtered })
  }),
]
