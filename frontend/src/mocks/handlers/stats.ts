import { http, HttpResponse } from 'msw'
import { previewBoss, previewCharacter, previewDate, previewPeriods } from '../fixtures/nexonPreview'

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

  http.get('*/api/v1/stats/boss-items', ({ request }) => HttpResponse.json({
    success: true,
    data: selectedPeriods(request).flatMap((period) => period.items
      .filter((item) => item.acquired)
      .map((item) => ({
        acquiredDate: previewDate,
        characterName: previewCharacter(period.characterId)?.characterName ?? '',
        bossName: previewBoss(period.bossId)?.bossName ?? '',
        difficulty: previewBoss(period.bossId)?.difficulty ?? 'NORMAL',
        itemName: item.dropItem.itemName,
      }))),
  })),
]
