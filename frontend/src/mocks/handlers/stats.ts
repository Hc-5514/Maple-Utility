import { http, HttpResponse } from 'msw'
import { previewBoss, previewCharacter, previewDate, previewPeriods } from '../fixtures/nexonPreview'
import { previewHistory } from '../fixtures/nexonPreviewHistory'
import type { BossDifficulty, StatsBossItem } from '../../types'

function selectedPeriods(request: Request) {
  const params = new URL(request.url).searchParams
  const characterId = params.has('characterId') ? Number(params.get('characterId')) : null
  const from = params.get('dateFrom')
  const to = params.get('dateTo')
  if (from && previewDate < from || to && previewDate > to) return []
  return previewPeriods(characterId)
}

export const statsHandlers = [
  http.get('*/api/v1/stats/hunting', () => HttpResponse.json({
    success: true,
    data: {
      totalMeso: 0, totalSolErda: 0, avgDailyMeso: 0,
      avgDailySolErda: 0, dailyRecords: [],
    },
  })),

  http.get('*/api/v1/stats/crystal', ({ request }) => {
    const details = selectedPeriods(request).flatMap((period) => {
      const crystal = period.items.find((item) => item.dropItem.itemKind === 'CRYSTAL')
      const boss = previewBoss(period.bossId)
      if (!crystal?.acquired || crystal.mesoAmount === null || !boss) return []
      return [{ bossName: boss.bossName, difficulty: boss.difficulty, income: crystal.mesoAmount }]
    })
    const totalIncome = details.reduce((sum, detail) => sum + detail.income, 0)
    return HttpResponse.json({ success: true, data: {
      totalCrystalIncome: totalIncome,
      weeklyAverage: totalIncome,
      weeklyRecords: details.length ? [{
        weekStart: '2026-10-01', totalIncome, bossDetails: details,
      }] : [],
    } })
  }),

  http.get('*/api/v1/stats/boss-items', ({ request }) => {
    const params = new URL(request.url).searchParams
    const page = Number(params.get('page') ?? '0')
    const size = Number(params.get('size') ?? '10')
    if (!Number.isInteger(page) || page < 0 || ![10, 20, 30].includes(size)) {
      return HttpResponse.json({ code: 'INVALID_PAGE_REQUEST', message: '페이지 요청 오류' }, { status: 400 })
    }

    const characterId = params.has('characterId') ? Number(params.get('characterId')) : null
    const from = params.get('dateFrom')
    const to = params.get('dateTo')
    const savedItems: StatsBossItem[] = selectedPeriods(request).flatMap((period) => period.items
      .filter((item) => item.acquired && item.dropItem.itemKind !== 'CRYSTAL')
      .map((item) => ({
        acquiredDate: previewDate,
        characterName: previewCharacter(period.characterId)?.characterName ?? '',
        bossName: previewBoss(period.bossId)?.bossName ?? '',
        difficulty: (previewBoss(period.bossId)?.difficulty ?? 'NORMAL') as BossDifficulty,
        itemName: item.dropItem.itemName,
      })))
    const rows = [...previewHistory.filter((item) =>
      (characterId === null || item.characterName === previewCharacter(characterId)?.characterName)
      && (!from || item.acquiredDate >= from)
      && (!to || item.acquiredDate <= to)), ...savedItems]
      .sort((a, b) => b.acquiredDate.localeCompare(a.acquiredDate))

    return HttpResponse.json({ success: true, data: {
      content: rows.slice(page * size, (page + 1) * size),
      totalElements: rows.length,
      totalPages: Math.ceil(rows.length / size),
      page,
      size,
    } })
  }),
]
