import { http, HttpResponse } from 'msw'
import {
  previewBossRecords, previewCharacter, previewCharacters, previewDate, previewWeekStart,
} from '../fixtures/nexonPreview'
import type { BossCandidate, SchedulerBossRecord } from '../../types'

const syncedAt = `${previewDate}T00:00:00+09:00`
const manualBossRecords = new Map<string, SchedulerBossRecord[]>()

function bossCandidates(resetPeriod: 'WEEKLY' | 'MONTHLY'): BossCandidate[] {
  const seen = new Set<number>()
  return previewBossRecords(1)
    .filter((record) => record.resetPeriod === resetPeriod && record.bossId && !seen.has(record.bossId) && Boolean(seen.add(record.bossId)))
    .map((record) => ({
      id: record.bossId ?? 0,
      bossName: record.bossName ?? '',
      difficulty: record.difficulty ?? 'NORMAL',
      bossImage: record.bossImage ?? null,
      crystalPrice: record.crystalPrice ?? 0,
      resetPeriod,
    }))
}

function dailyRecords(characterId: number) {
  return (previewCharacter(characterId)?.daily ?? []).map((item, index) => ({
    id: characterId * 100 + index,
    characterId,
    recordDate: previewDate,
    contentName: item.contentName,
    completedCount: item.nowCount,
    totalCount: Math.max(1, item.maxCount),
    syncedAt,
  }))
}

function weeklyRecords(characterId: number) {
  return (previewCharacter(characterId)?.weekly ?? []).map((item, index) => ({
    id: characterId * 100 + index,
    characterId,
    weekStartDate: previewWeekStart,
    contentName: item.contentName,
    isCompleted: item.questState === '1' || (item.maxCount > 0 && item.nowCount >= item.maxCount),
    score: item.contentName.includes('지하 수로') ? item.nowCount : null,
    syncedAt,
  }))
}

export const schedulerHandlers = [
  http.post('*/api/v1/scheduler/sync', () => HttpResponse.json({
    success: true,
    data: {
      id: 2, jobType: 'SCHEDULER', status: 'COMPLETED', totalCount: 2,
      completedCount: 2, skippedCount: 0, errorMessage: null, startedAt: syncedAt, completedAt: syncedAt,
    },
  })),

  http.get('*/api/v1/scheduler/sync-jobs/:jobId', () => HttpResponse.json({
    success: true,
    data: {
      id: 2, jobType: 'SCHEDULER', status: 'COMPLETED', totalCount: 2,
      completedCount: 2, skippedCount: 0, errorMessage: null, startedAt: syncedAt, completedAt: syncedAt,
    },
  })),

  http.get('*/api/v1/scheduler/:characterId/daily', ({ params }) => HttpResponse.json({
    success: true, data: dailyRecords(Number(params.characterId)),
  })),

  http.get('*/api/v1/scheduler/:characterId/weekly', ({ params }) => HttpResponse.json({
    success: true, data: weeklyRecords(Number(params.characterId)),
  })),

  http.get('*/api/v1/scheduler/:characterId/boss/candidates', ({ request }) => {
    const resetPeriod = new URL(request.url).searchParams.get('resetPeriod')
    if (resetPeriod !== 'WEEKLY' && resetPeriod !== 'MONTHLY') {
      return HttpResponse.json({ success: false }, { status: 400 })
    }
    return HttpResponse.json({ success: true, data: bossCandidates(resetPeriod) })
  }),

  http.post('*/api/v1/scheduler/:characterId/boss/manual', async ({ params, request }) => {
    const characterId = Number(params.characterId)
    const body = await request.json() as { periodStart: string; resetPeriod: 'WEEKLY' | 'MONTHLY'; bossIds: number[] }
    if (body.resetPeriod === 'WEEKLY' && body.bossIds.length > 12) {
      return HttpResponse.json({ success: false }, { status: 400 })
    }
    const normalizedStart = body.resetPeriod === 'MONTHLY' ? `${body.periodStart.slice(0, 7)}-01` : body.periodStart
    const key = `${characterId}:${normalizedStart}:${body.resetPeriod}`
    if (manualBossRecords.has(key)) return HttpResponse.json({ success: false }, { status: 409 })
    const byId = new Map(bossCandidates(body.resetPeriod).map((boss) => [boss.id, boss]))
    const records: SchedulerBossRecord[] = body.bossIds.flatMap((bossId, index) => {
      const boss = byId.get(bossId)
      if (!boss) return []
      return [{
        id: 900000 + index,
        characterId,
        recordDate: normalizedStart,
        bossId,
        bossName: boss.bossName,
        difficulty: boss.difficulty,
        bossImage: boss.bossImage,
        crystalPrice: boss.crystalPrice,
        resetPeriod: body.resetPeriod,
        isCompleted: true,
        syncedAt: null,
      }]
    })
    manualBossRecords.set(key, records)
    return HttpResponse.json({ success: true, data: records })
  }),

  http.get('*/api/v1/scheduler/:characterId/boss', ({ params, request }) => {
    const characterId = Number(params.characterId)
    const date = new URL(request.url).searchParams.get('date') ?? previewDate
    const records = [
      ...previewBossRecords(characterId),
      ...(manualBossRecords.get(`${characterId}:${date}:WEEKLY`) ?? []),
      ...(manualBossRecords.get(`${characterId}:${date.slice(0, 7)}-01:MONTHLY`) ?? []),
    ]
    return HttpResponse.json({ success: true, data: {
      weeklyBosses: records.filter((record) => record.resetPeriod === 'WEEKLY'),
      monthlyBosses: records.filter((record) => record.resetPeriod === 'MONTHLY'),
    } })
  }),

  http.get('*/api/v1/scheduler/:characterId/guild', ({ params }) => {
    const characterId = Number(params.characterId)
    const guild = previewCharacter(characterId)?.weekly.find((item) => item.contentName.includes('지하 수로'))
    return HttpResponse.json({ success: true, data: guild ? [{
      id: characterId, characterId, recordDate: previewDate,
      contentName: '지하 수로', score: guild.nowCount, syncedAt,
    }] : [] })
  }),

  http.get('*/api/v1/scheduler/summary', () => HttpResponse.json({
    success: true,
    data: {
      characters: previewCharacters.map((character) => {
        const daily = dailyRecords(character.id)
        const weekly = weeklyRecords(character.id)
        const bosses = previewBossRecords(character.id)
        const weeklyBosses = bosses.filter((boss) => boss.resetPeriod === 'WEEKLY')
        const monthlyBosses = bosses.filter((boss) => boss.resetPeriod === 'MONTHLY')
        return {
          characterId: character.id,
          characterName: character.characterName,
          characterLevel: character.characterLevel,
          characterClass: character.characterClass,
          characterImage: character.characterImage,
          worldName: character.worldName,
          daily: {
            completed: daily.reduce((sum, record) => sum + Math.min(record.completedCount, record.totalCount), 0),
            total: daily.reduce((sum, record) => sum + record.totalCount, 0),
          },
          weekly: { completed: weekly.filter((record) => record.isCompleted).length, total: weekly.length },
          weeklyBoss: { completed: weeklyBosses.filter((boss) => boss.isCompleted).length, total: weeklyBosses.length },
          monthlyBoss: { completed: monthlyBosses.filter((boss) => boss.isCompleted).length, total: monthlyBosses.length },
        }
      }),
      syncedAt,
    },
  })),
]
