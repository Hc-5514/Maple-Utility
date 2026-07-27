import { http, HttpResponse } from 'msw'
import schedulerFixture from '../fixtures/scheduler.json'

const dailyRecords = [...schedulerFixture.daily]
const weeklyRecords = [...schedulerFixture.weekly]
const bossRecords = [...schedulerFixture.boss]
const guildRecords = [
  { id: 1, characterId: 1, recordDate: '2026-07-14', contentName: '지하 수로', score: 45000, syncedAt: '2026-07-14T09:00:00' },
  { id: 2, characterId: 1, recordDate: '2026-07-14', contentName: '플래그 레이스', score: null as number | null, syncedAt: null },
]

const bossInfoMap: Record<number, { bossName: string; difficulty: string; bossImage: string | null; crystalPrice: number }> = {
  2:  { bossName: '시그너스',    difficulty: 'NORMAL',  bossImage: null, crystalPrice: 1500000   },
  5:  { bossName: '데미안',      difficulty: 'HARD',    bossImage: null, crystalPrice: 14400000  },
  10: { bossName: '루시드',      difficulty: 'HARD',    bossImage: null, crystalPrice: 14400000  },
  13: { bossName: '윌',          difficulty: 'HARD',    bossImage: null, crystalPrice: 14400000  },
  16: { bossName: '더스크',      difficulty: 'CHAOS',   bossImage: null, crystalPrice: 21600000  },
  18: { bossName: '도원결의',    difficulty: 'HARD',    bossImage: null, crystalPrice: 21600000  },
  20: { bossName: '세렌',        difficulty: 'HARD',    bossImage: null, crystalPrice: 21600000  },
  24: { bossName: '칼로스',      difficulty: 'EXTREME', bossImage: null, crystalPrice: 25000000  },
  27: { bossName: '검은 마법사', difficulty: 'NORMAL',  bossImage: null, crystalPrice: 21600000  },
  35: { bossName: '진 힐라',     difficulty: 'CHAOS',   bossImage: null, crystalPrice: 25000000  },
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
        ...(bossInfoMap[r.bossId] ?? { bossName: `보스#${r.bossId}`, difficulty: 'NORMAL', bossImage: null, crystalPrice: 0 }),
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

  // 전체 요약 (BE: GET /scheduler/summary) — per-character grouped format (BE-19 기준)
  http.get('/api/v1/scheduler/summary', () => {
    const today = new Date().toISOString().split('T')[0]
    const weekStart = '2026-07-13'

    const charDailyAll = dailyRecords.filter((r) => r.recordDate === today)
    const charWeeklyAll = weeklyRecords.filter((r) => r.weekStartDate === weekStart)
    const weeklyBossAll = bossRecords.filter((r) => r.resetPeriod === 'WEEKLY')
    const monthlyBossAll = bossRecords.filter((r) => r.resetPeriod === 'MONTHLY')

    const syncedAt =
      [...charDailyAll, ...charWeeklyAll]
        .map((r) => r.syncedAt)
        .filter(Boolean)
        .slice(-1)[0] ?? new Date().toISOString()

    return HttpResponse.json({
      success: true,
      data: {
        characters: [
          {
            characterId: 1,
            characterName: '달빛제로',
            characterLevel: 292,
            characterClass: '아크메이지(썬,콜)',
            characterImage: null,
            worldName: '스카니아',
            daily: {
              completed: charDailyAll.reduce((s, r) => s + r.completedCount, 0),
              total: charDailyAll.reduce((s, r) => s + r.totalCount, 0),
            },
            weekly: {
              completed: charWeeklyAll.filter((r) => r.isCompleted).length,
              total: charWeeklyAll.length,
            },
            weeklyBoss: {
              completed: weeklyBossAll.filter((r) => r.isCompleted).length,
              total: weeklyBossAll.length,
            },
            monthlyBoss: {
              completed: monthlyBossAll.filter((r) => r.isCompleted).length,
              total: monthlyBossAll.length,
            },
          },
        ],
        syncedAt,
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

]
