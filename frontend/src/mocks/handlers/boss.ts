import { http, HttpResponse } from 'msw'
import { getPreviewPeriod, previewBoss, savePreviewPeriod } from '../fixtures/nexonPreview'
import type { BossPeriod } from '../../types'

export const bossHandlers = [
  http.get('*/api/v1/boss/:bossId/drop-items', ({ params }) => {
    const boss = previewBoss(Number(params.bossId))
    if (!boss) return HttpResponse.json({ success: false }, { status: 404 })
    return HttpResponse.json({ success: true, data: boss.dropItems })
  }),

  http.get('*/api/v1/boss/:bossId/period', ({ params, request }) => {
    const url = new URL(request.url)
    const period = getPreviewPeriod(
      Number(url.searchParams.get('characterId')),
      Number(params.bossId),
      url.searchParams.get('periodStart') ?? '',
    )
    if (!period) return HttpResponse.json({ success: false }, { status: 404 })
    return HttpResponse.json({ success: true, data: period })
  }),

  http.put('*/api/v1/boss/:bossId/period', async ({ params, request }) => {
    const bossId = Number(params.bossId)
    const body = await request.json() as {
      characterId: number
      periodStart: string
      partySize: number
      crystalPrice?: number | null
      items: { bossDropItemId: number; acquired: boolean; quantity: number }[]
    }
    const previous = getPreviewPeriod(body.characterId, bossId, body.periodStart)
    if (!previous || body.partySize < 1 || body.partySize > previous.maxPartySize) {
      return HttpResponse.json({ success: false }, { status: 400 })
    }
    const changed = new Map(body.items.map((item) => [item.bossDropItemId, item]))
    // 수정한 결정 가격(미전송이면 기존 적용 가격 유지)
    const crystalPrice = body.crystalPrice ?? previous.crystalPrice
    if (crystalPrice < 0 || crystalPrice > 100_000_000_000) {
      return HttpResponse.json({ success: false }, { status: 400 })
    }
    const saved: BossPeriod = {
      ...previous,
      partySize: body.partySize,
      crystalPrice,
      items: previous.items.map((item) => {
        const edit = changed.get(item.dropItem.id)
        return edit ? {
          ...item,
          acquired: edit.acquired,
          quantity: edit.quantity,
          // 결정 메소는 서버가 결정 가격 / 인원수로 계산
          mesoAmount: item.dropItem.itemKind === 'CRYSTAL' && crystalPrice > 0
            ? Math.floor(crystalPrice / body.partySize)
            : null,
        } : { ...item, acquired: false }
      }),
    }
    return HttpResponse.json({ success: true, data: savePreviewPeriod(saved) })
  }),
]
