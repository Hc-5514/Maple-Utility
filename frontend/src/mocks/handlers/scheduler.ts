import { http, HttpResponse } from 'msw'
import schedulerFixture from '../fixtures/scheduler.json'

const dailyRecords = [...schedulerFixture.daily]
const weeklyRecords = [...schedulerFixture.weekly]
const bossRecords = [...schedulerFixture.boss]
const guildRecords = [
  { id: 1, characterId: 1, recordDate: '2026-07-14', contentName: '지하 수로', score: 45000, syncedAt: '2026-07-14T09:00:00' },
  { id: 2, characterId: 1, recordDate: '2026-07-14', contentName: '플래그 레이스', score: null as number | null, syncedAt: null },
]

const bossNameMap: Record<number, { bossName: string; difficulty: string }> = {
  2:  { bossName: '시그너스',        difficulty: 'NORMAL' },
  5:  { bossName: '데미안',          difficulty: 'HARD'   },
  10: { bossName: '루시드',          difficulty: 'HARD'   },
  13: { bossName: '윌',              difficulty: 'HARD'   },
  16: { bossName: '더스크',          difficulty: 'CHAOS'  },
  18: { bossName: '도원결의',        difficulty: 'HARD'   },
  20: { bossName: '세렌',            difficulty: 'HARD'   },
  24: { bossName: '칼로스',          difficulty: 'EXTREME'},
  27: { bossName: '검은 마법사',     difficulty: 'NORMAL' },
  35: { bossName: '진 힐라',         difficulty: 'CHAOS'  },
}

export const schedulerHandlers = [
  // 캐릭터별 일간 기록 (BE: GET /scheduler/:characterId/daily?date=)
  http.get('/api/v1/scheduler/:characterId/daily', ({ params, request }) => {
    const characterId = Number(params.characterId)
    const url = new URL(request.url)
    const date = url.searchParams.get('date') ?? new Date().toISOString().split('T')[0]
    const records = dailyRecords.filter(
      (r) => r.characterId === characterId && r.recordDate === date,
    )
    return HttpResponse.json({ success: true, data: records })
  }),

  // 캐릭터별 주간 기록 (BE: GET /scheduler/:characterId/weekly?date=)
  http.get('/api/v1/scheduler/:characterId/weekly', ({ params, request }) => {
    const characterId = Number(params.characterId)
    const url = new URL(request.url)
    const weekStart = url.searchParams.get('date') ?? '2026-07-13'
    const records = weeklyRecords.filter(
      (r) => r.characterId === characterId && r.weekStartDate === weekStart,
    )
    return HttpResponse.json({ success: true, data: records })
  }),

  // 캐릭터별 보스 기록 (BE: GET /scheduler/:characterId/boss?date=)
  http.get('/api/v1/scheduler/:characterId/boss', ({ params, request }) => {
    const characterId = Number(params.characterId)
    const url = new URL(request.url)
    const date = url.searchParams.get('date') ?? new Date().toISOString().split('T')[0]
    const records = bossRecords
      .filter((r) => r.characterId === characterId && r.recordDate === date)
      .map((r) => ({
        ...r,
        ...(bossNameMap[r.bossId] ?? { bossName: `보스#${r.bossId}`, difficulty: 'NORMAL' }),
        completed: r.isCompleted,
      }))
    return HttpResponse.json({ success: true, data: records })
  }),

  // 캐릭터별 길드 기록 (BE: GET /scheduler/:characterId/guild?date=)
  http.get('/api/v1/scheduler/:characterId/guild', ({ params, request }) => {
    const characterId = Number(params.characterId)
    const url = new URL(request.url)
    const date = url.searchParams.get('date') ?? new Date().toISOString().split('T')[0]
    const records = guildRecords.filter(
      (r) => r.characterId === characterId && r.recordDate === date,
    )
    return HttpResponse.json({ success: true, data: records })
  }),

  // 전체 요약 (BE: GET /scheduler/summary) — BE flat format 반환
  http.get('/api/v1/scheduler/summary', () => {
    const today = new Date().toISOString().split('T')[0]
    const weekStart = '2026-07-13'

    const charDailyAll = dailyRecords.filter((r) => r.recordDate === today)
    const charWeeklyAll = weeklyRecords.filter((r) => r.weekStartDate === weekStart)
    const allBoss = bossRecords.map((r) => ({
      characterId: r.characterId,
      characterName: '달빛제로',
      ...(bossNameMap[r.bossId] ?? { bossName: `보스#${r.bossId}`, difficulty: 'NORMAL' }),
      completed: r.isCompleted,
      resetPeriod: r.resetPeriod,
      syncedAt: r.syncedAt,
    }))

    return HttpResponse.json({
      success: true,
      data: {
        daily: charDailyAll.map((r) => ({ ...r, characterName: '달빛제로' })),
        weekly: charWeeklyAll.map((r) => ({
          ...r,
          characterName: '달빛제로',
          completed: r.isCompleted,
        })),
        weeklyBoss: allBoss.filter((r) => r.resetPeriod === 'WEEKLY'),
        monthlyBoss: allBoss.filter((r) => r.resetPeriod === 'MONTHLY'),
      },
    })
  }),

  // BE 미구현 — MSW Mock only (Troubleshooting 기록됨)
  http.put('/api/v1/scheduler/daily/:id', async ({ params, request }) => {
    const body = (await request.json()) as { completedCount?: number }
    const record = dailyRecords.find((r) => r.id === Number(params.id))
    if (!record) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    if (body.completedCount !== undefined) record.completedCount = body.completedCount
    record.syncedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: record })
  }),

  http.put('/api/v1/scheduler/weekly/:id', async ({ params, request }) => {
    const body = (await request.json()) as { isCompleted?: boolean; score?: number }
    const record = weeklyRecords.find((r) => r.id === Number(params.id))
    if (!record) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    if (body.isCompleted !== undefined) record.isCompleted = body.isCompleted
    if (body.score !== undefined) record.score = body.score
    record.syncedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: record })
  }),

  http.put('/api/v1/scheduler/boss/:id', async ({ params, request }) => {
    const body = (await request.json()) as { completed?: boolean }
    const record = bossRecords.find((r) => r.id === Number(params.id))
    if (!record) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    if (body.completed !== undefined) record.isCompleted = body.completed
    record.syncedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: record })
  }),
]
