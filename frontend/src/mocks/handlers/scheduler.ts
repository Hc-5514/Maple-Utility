import { http, HttpResponse } from 'msw'
import {
  previewBossRecords, previewCharacter, previewCharacters, previewDate, previewWeekStart,
} from '../fixtures/nexonPreview'
import catalog from '../fixtures/nexonPreviewCatalog.json'
import type { BossCandidate, SchedulerBossRecord } from '../../types'
import { localDate, seoulDate, thursdayWeekStart } from '../../utils/date'

const syncedAt = `${previewDate}T00:00:00+09:00`
const manualBossRecords = new Map<string, SchedulerBossRecord[]>()
const periodSelections = new Map<string, SchedulerBossRecord>()
const hiddenBosses = new Set<string>()

function selectionKey(characterId: number, periodStart: string, resetPeriod: string, bossId: number) {
  return `${characterId}:${periodStart}:${resetPeriod}:${bossId}`
}

const difficultyOrder = ['EASY', 'NORMAL', 'HARD', 'CHAOS', 'EXTREME']

function bossCandidates(resetPeriod: 'WEEKLY' | 'MONTHLY'): BossCandidate[] {
  const bosses = catalog.filter((boss) => boss.resetPeriod === resetPeriod)
  const nameOrder = [...new Set(bosses.map((boss) => boss.bossName))]
  return [...bosses]
    .sort((a, b) => nameOrder.indexOf(a.bossName) - nameOrder.indexOf(b.bossName)
      || difficultyOrder.indexOf(a.difficulty) - difficultyOrder.indexOf(b.difficulty))
    .map((boss) => ({
      id: boss.id,
      bossName: boss.bossName,
      difficulty: boss.difficulty as BossCandidate['difficulty'],
      bossImage: null,
      crystalPrice: boss.crystalPrice,
      resetPeriod,
    }))
}

const bossLimits = { WEEKLY: 12, MONTHLY: 1 } as const

// 이번 주·지난주(목~수)와 해당 주차에 걸친 달의 보스 선택 잠금
function customizable(periodStart: string, resetPeriod: 'WEEKLY' | 'MONTHLY') {
  const today = new Date(`${seoulDate()}T12:00:00`)
  const lockedFrom = new Date(`${thursdayWeekStart(today)}T12:00:00`)
  lockedFrom.setDate(lockedFrom.getDate() - 7)
  if (resetPeriod === 'MONTHLY') {
    const monthEnd = new Date(`${periodStart.slice(0, 7)}-01T12:00:00`)
    monthEnd.setMonth(monthEnd.getMonth() + 1, 0)
    return localDate(monthEnd) < localDate(lockedFrom)
  }
  return periodStart < localDate(lockedFrom)
}

function periodBossRecords(characterId: number, weeklyDate: string, monthlyDate: string) {
  const monthlyStart = `${monthlyDate.slice(0, 7)}-01`
  const records = [
    ...previewBossRecords(characterId),
    ...(manualBossRecords.get(`${characterId}:${weeklyDate}:WEEKLY`) ?? []),
    ...(manualBossRecords.get(`${characterId}:${monthlyStart}:MONTHLY`) ?? []),
    ...periodSelections.values(),
  ].filter((record) => {
    const start = record.resetPeriod === 'MONTHLY' ? monthlyStart : weeklyDate
    const inPeriod = record.recordDate === start || (record.recordDate === previewDate && (
      record.resetPeriod === 'WEEKLY' ? start === previewWeekStart : start === `${previewDate.slice(0, 7)}-01`
    ))
    return record.characterId === characterId && inPeriod && !hiddenBosses.has(selectionKey(characterId, start, record.resetPeriod, record.bossId ?? 0))
  })
  return [...new Map(records.map((record) => [selectionKey(characterId, record.resetPeriod === 'MONTHLY' ? monthlyStart : weeklyDate, record.resetPeriod, record.bossId ?? 0), record])).values()]
}

// Mirrors the backend: only user additions are limited; synced snapshots may exceed the limit.
function invalidSelection(resetPeriod: 'WEEKLY' | 'MONTHLY', added: string[], existing: string[] = []) {
  if (added.length === 0) return false
  return existing.length + added.length > bossLimits[resetPeriod]
    || new Set(added).size !== added.length
    || added.some((name) => existing.includes(name))
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
    const names = new Map(bossCandidates(body.resetPeriod).map((boss) => [boss.id, boss.bossName]))
    if (!customizable(body.resetPeriod === 'MONTHLY' ? `${body.periodStart.slice(0, 7)}-01` : body.periodStart, body.resetPeriod)) {
      return HttpResponse.json({ success: false }, { status: 400 })
    }
    if (invalidSelection(body.resetPeriod, body.bossIds.map((id) => names.get(id) ?? String(id)))) {
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

  http.post('*/api/v1/scheduler/:characterId/boss/selection', async ({ params, request }) => {
    const characterId = Number(params.characterId)
    const body = await request.json() as { periodStart: string; resetPeriod: 'WEEKLY' | 'MONTHLY'; bossIds: number[] }
    const start = body.resetPeriod === 'MONTHLY' ? `${body.periodStart.slice(0, 7)}-01` : body.periodStart
    if (!customizable(start, body.resetPeriod)) return HttpResponse.json({ success: false }, { status: 400 })
    const candidates = new Map(bossCandidates(body.resetPeriod).map((boss) => [boss.id, boss]))
    const existing = periodBossRecords(characterId, start, start).filter((record) => record.resetPeriod === body.resetPeriod)
    const existingIds = new Set(existing.map((record) => record.bossId))
    const added = body.bossIds.filter((id) => !existingIds.has(id)).map((id) => candidates.get(id)?.bossName ?? String(id))
    if (invalidSelection(body.resetPeriod, added, existing.map((record) => record.bossName ?? ''))) {
      return HttpResponse.json({ success: false }, { status: 400 })
    }
    const records = body.bossIds.flatMap((bossId) => {
      const boss = candidates.get(bossId)
      if (!boss) return []
      const record: SchedulerBossRecord = {
        id: 900000 + bossId, characterId, recordDate: start, bossId,
        bossName: boss.bossName, difficulty: boss.difficulty, bossImage: boss.bossImage,
        crystalPrice: boss.crystalPrice, resetPeriod: body.resetPeriod,
        isCompleted: true, syncedAt: null,
      }
      const key = selectionKey(characterId, start, body.resetPeriod, bossId)
      hiddenBosses.delete(key)
      periodSelections.set(key, record)
      return [record]
    })
    return HttpResponse.json({ success: true, data: records })
  }),

  http.delete('*/api/v1/scheduler/:characterId/boss/selection/:bossId', ({ params, request }) => {
    const characterId = Number(params.characterId)
    const resetPeriod = new URL(request.url).searchParams.get('resetPeriod')
    const periodStart = new URL(request.url).searchParams.get('periodStart')
    if (!resetPeriod || !periodStart) return HttpResponse.json({ success: false }, { status: 400 })
    if (!customizable(periodStart, resetPeriod as 'WEEKLY' | 'MONTHLY')) return HttpResponse.json({ success: false }, { status: 400 })
    const key = selectionKey(characterId, periodStart, resetPeriod, Number(params.bossId))
    periodSelections.delete(key)
    hiddenBosses.add(key)
    return HttpResponse.json({ success: true, data: null })
  }),

  http.get('*/api/v1/scheduler/:characterId/boss', ({ params, request }) => {
    const characterId = Number(params.characterId)
    const searchParams = new URL(request.url).searchParams
    const weeklyDate = searchParams.get('weeklyDate') ?? searchParams.get('date') ?? previewDate
    const monthlyDate = searchParams.get('monthlyDate') ?? searchParams.get('date') ?? previewDate
    const unique = periodBossRecords(characterId, weeklyDate, monthlyDate)
    return HttpResponse.json({ success: true, data: {
      weeklyBosses: unique.filter((record) => record.resetPeriod === 'WEEKLY'),
      monthlyBosses: unique.filter((record) => record.resetPeriod === 'MONTHLY'),
      weeklyCustomizable: customizable(weeklyDate, 'WEEKLY'),
      monthlyCustomizable: customizable(`${monthlyDate.slice(0, 7)}-01`, 'MONTHLY'),
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

  http.get('*/api/v1/scheduler/summary', ({ request }) => HttpResponse.json({
    success: true,
    data: {
      characters: previewCharacters.map((character) => {
        const daily = dailyRecords(character.id)
        const weekly = weeklyRecords(character.id)
        const bosses = previewBossRecords(character.id)
        const weeklyBosses = bosses.filter((boss) => boss.resetPeriod === 'WEEKLY')
        const monthlyBosses = bosses.filter((boss) => boss.resetPeriod === 'MONTHLY')
        return {
          hasRecords: !new URL(request.url).searchParams.get('date') || new URL(request.url).searchParams.get('date') === previewDate,
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
          // Cleared bosses out of the in-game clear limit, independent of registered scheduler entries.
          weeklyBoss: { completed: weeklyBosses.filter((boss) => boss.isCompleted).length, total: weeklyBosses.length > 0 ? bossLimits.WEEKLY : 0 },
          monthlyBoss: { completed: monthlyBosses.filter((boss) => boss.isCompleted).length, total: monthlyBosses.length > 0 ? bossLimits.MONTHLY : 0 },
        }
      }),
      syncedAt,
    },
  })),
]
