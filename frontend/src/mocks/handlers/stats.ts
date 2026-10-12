import { http, HttpResponse } from 'msw'
import { previewBoss, previewCharacter, previewDate, previewPeriods } from '../fixtures/nexonPreview'
import { previewHistory } from '../fixtures/nexonPreviewHistory'
import { previewBossNames, previewCrystalWeeks, septemberBossItems } from '../fixtures/statsPreview'
import { currentHuntingRecords, isFavoriteCharacter } from './hunting'
import type { BossDifficulty, StatsBossItem, StatsCrystalWeekly } from '../../types'

const inRange = (date: string, from: string | null, to: string | null) =>
  (!from || date >= from) && (!to || date <= to)

function selectedPeriods(request: Request) {
  const params = new URL(request.url).searchParams
  const characterId = params.has('characterId') ? Number(params.get('characterId')) : null
  const from = params.get('dateFrom')
  const to = params.get('dateTo')
  if (from && previewDate < from || to && previewDate > to) return []
  return previewPeriods(characterId)
}

export const statsHandlers = [
  http.get('*/api/v1/stats/hunting', ({ request }) => {
    const params = new URL(request.url).searchParams
    const characterId = params.has('characterId') ? Number(params.get('characterId')) : null
    const from = params.get('dateFrom')
    const to = params.get('dateTo')
    const daily = new Map<string, { date: string; mesoEarned: number; solErdaEarned: number; playDurationMin: number }>()
    currentHuntingRecords()
      .filter((record) => isFavoriteCharacter(record.characterId)
        && (characterId === null || record.characterId === characterId)
        && inRange(record.recordDate, from, to))
      .forEach((record) => {
        const day = daily.get(record.recordDate) ?? { date: record.recordDate, mesoEarned: 0, solErdaEarned: 0, playDurationMin: 0 }
        day.mesoEarned += record.mesoEarned
        day.solErdaEarned += record.solErdaEarned
        day.playDurationMin += record.playDurationMin ?? 0
        daily.set(record.recordDate, day)
      })
    const dailyRecords = [...daily.values()].sort((a, b) => a.date.localeCompare(b.date))
    const totalMeso = dailyRecords.reduce((sum, day) => sum + day.mesoEarned, 0)
    const totalSolErda = dailyRecords.reduce((sum, day) => sum + day.solErdaEarned, 0)
    const days = dailyRecords.length
    return HttpResponse.json({ success: true, data: {
      totalMeso,
      totalSolErda,
      avgDailyMeso: days ? Math.floor(totalMeso / days) : 0,
      avgDailySolErda: days ? Math.floor(totalSolErda / days) : 0,
      dailyRecords,
    } })
  }),

  http.get('*/api/v1/stats/crystal', ({ request }) => {
    const params = new URL(request.url).searchParams
    const characterId = params.has('characterId') ? Number(params.get('characterId')) : null
    const details = selectedPeriods(request).flatMap((period) => {
      const crystal = period.items.find((item) => item.dropItem.itemKind === 'CRYSTAL')
      const boss = previewBoss(period.bossId)
      if (!crystal?.acquired || crystal.mesoAmount === null || !boss) return []
      return [{ weekStart: '2026-10-01', bossName: boss.bossName, difficulty: boss.difficulty, income: crystal.mesoAmount }]
    })
    const seeded = previewCrystalWeeks
      .filter((week) => (characterId === null || week.characterId === characterId)
        && inRange(week.weekStart, params.get('dateFrom'), params.get('dateTo')))
    const weeks = new Map<string, StatsCrystalWeekly>()
    ;[...seeded, ...details].forEach(({ weekStart, bossName, difficulty, income }) => {
      const week = weeks.get(weekStart) ?? { weekStart, totalIncome: 0, bossDetails: [] }
      week.totalIncome += income
      week.bossDetails.push({ bossName, difficulty: difficulty as BossDifficulty, income })
      weeks.set(weekStart, week)
    })
    const weeklyRecords = [...weeks.values()].sort((a, b) => a.weekStart.localeCompare(b.weekStart))
    const totalIncome = weeklyRecords.reduce((sum, week) => sum + week.totalIncome, 0)
    return HttpResponse.json({ success: true, data: {
      totalCrystalIncome: totalIncome,
      weeklyAverage: weeklyRecords.length ? Math.floor(totalIncome / weeklyRecords.length) : 0,
      weeklyRecords,
    } })
  }),

  http.get('*/api/v1/stats/bosses', () => HttpResponse.json({ success: true, data: previewBossNames })),

  http.get('*/api/v1/stats/boss-items', ({ request }) => {
    const params = new URL(request.url).searchParams
    const page = Number(params.get('page') ?? '0')
    const size = Number(params.get('size') ?? '10')
    if (!Number.isInteger(page) || page < 0 || ![10, 20, 30].includes(size)) {
      return HttpResponse.json({ code: 'INVALID_PAGE_REQUEST', message: '페이지 요청 오류' }, { status: 400 })
    }

    const characterId = params.has('characterId') ? Number(params.get('characterId')) : null
    const bossName = params.get('bossName')?.trim() || null
    const from = params.get('dateFrom')
    const to = params.get('dateTo')
    const september = septemberBossItems({
      1: previewCharacter(1)?.characterName ?? '',
      2: previewCharacter(2)?.characterName ?? '',
    })
    const savedItems: StatsBossItem[] = selectedPeriods(request).flatMap((period) => period.items
      .filter((item) => item.acquired && item.dropItem.itemKind !== 'CRYSTAL')
      .map((item) => ({
        acquiredDate: previewDate,
        characterName: previewCharacter(period.characterId)?.characterName ?? '',
        bossName: previewBoss(period.bossId)?.bossName ?? '',
        difficulty: (previewBoss(period.bossId)?.difficulty ?? 'NORMAL') as BossDifficulty,
        itemName: item.dropItem.itemName,
      })))
    const rows = [...[...previewHistory, ...september].filter((item) =>
      (characterId === null || item.characterName === previewCharacter(characterId)?.characterName)
      && inRange(item.acquiredDate, from, to)), ...savedItems]
      .filter((item) => bossName === null || item.bossName === bossName)
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
