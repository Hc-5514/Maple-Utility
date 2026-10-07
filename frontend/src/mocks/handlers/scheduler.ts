import { http, HttpResponse } from 'msw'
import {
  previewBossRecords, previewCharacter, previewCharacters, previewDate, previewWeekStart,
} from '../fixtures/nexonPreview'

const syncedAt = `${previewDate}T00:00:00+09:00`

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

  http.get('*/api/v1/scheduler/:characterId/boss', ({ params }) => {
    const records = previewBossRecords(Number(params.characterId))
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
