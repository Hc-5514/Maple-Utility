import { http, HttpResponse } from 'msw'
import schedulerFixture from '../fixtures/scheduler.json'

const dailyRecords = [...schedulerFixture.daily]
const weeklyRecords = [...schedulerFixture.weekly]
const bossRecords = [...schedulerFixture.boss]
const guildRecords = [
  { id: 1, characterId: 1, recordDate: '2026-07-14', contentName: '지하 수로', score: 45000, syncedAt: '2026-07-14T09:00:00' },
  { id: 2, characterId: 1, recordDate: '2026-07-14', contentName: '플래그 레이스', score: null as number | null, syncedAt: null },
]

export const schedulerHandlers = [
  http.get('/api/v1/scheduler/daily', ({ request }) => {
    const url = new URL(request.url)
    const characterId = Number(url.searchParams.get('characterId'))
    const date = url.searchParams.get('date') ?? new Date().toISOString().split('T')[0]
    const records = dailyRecords.filter(
      r => r.characterId === characterId && r.recordDate === date,
    )
    return HttpResponse.json({ success: true, data: records })
  }),

  http.put('/api/v1/scheduler/daily/:id', async ({ params, request }) => {
    const body = (await request.json()) as { completedCount?: number }
    const record = dailyRecords.find(r => r.id === Number(params.id))
    if (!record) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    if (body.completedCount !== undefined) record.completedCount = body.completedCount
    record.syncedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: record })
  }),

  http.get('/api/v1/scheduler/weekly', ({ request }) => {
    const url = new URL(request.url)
    const characterId = Number(url.searchParams.get('characterId'))
    const weekStart = url.searchParams.get('weekStart') ?? '2026-07-13'
    const records = weeklyRecords.filter(
      r => r.characterId === characterId && r.weekStartDate === weekStart,
    )
    return HttpResponse.json({ success: true, data: records })
  }),

  http.put('/api/v1/scheduler/weekly/:id', async ({ params, request }) => {
    const body = (await request.json()) as { isCompleted?: boolean; score?: number }
    const record = weeklyRecords.find(r => r.id === Number(params.id))
    if (!record) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    if (body.isCompleted !== undefined) record.isCompleted = body.isCompleted
    if (body.score !== undefined) record.score = body.score
    record.syncedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: record })
  }),

  http.get('/api/v1/scheduler/boss', ({ request }) => {
    const url = new URL(request.url)
    const characterId = Number(url.searchParams.get('characterId'))
    const date = url.searchParams.get('date') ?? '2026-07-14'
    const records = bossRecords.filter(
      r => r.characterId === characterId && r.recordDate === date,
    )
    return HttpResponse.json({ success: true, data: records })
  }),

  http.put('/api/v1/scheduler/boss/:id', async ({ params, request }) => {
    const body = (await request.json()) as { isCompleted?: boolean }
    const record = bossRecords.find(r => r.id === Number(params.id))
    if (!record) {
      return HttpResponse.json({ success: false, message: '기록을 찾을 수 없음' }, { status: 404 })
    }
    if (body.isCompleted !== undefined) record.isCompleted = body.isCompleted
    record.syncedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: record })
  }),

  http.get('/api/v1/scheduler/guild', ({ request }) => {
    const url = new URL(request.url)
    const characterId = Number(url.searchParams.get('characterId'))
    const date = url.searchParams.get('date') ?? new Date().toISOString().split('T')[0]
    const records = guildRecords.filter(r => r.characterId === characterId && r.recordDate === date)
    return HttpResponse.json({ success: true, data: records })
  }),

  http.get('/api/v1/scheduler/summary', () => {
    const today = new Date().toISOString().split('T')[0]
    const weekStart = '2026-07-13'

    const charDaily = dailyRecords.filter(r => r.characterId === 1 && r.recordDate === today)
    const charWeekly = weeklyRecords.filter(r => r.characterId === 1 && r.weekStartDate === weekStart)
    const charBoss = bossRecords.filter(r => r.characterId === 1)
    const weeklyBoss = charBoss.filter(r => r.resetPeriod === 'WEEKLY')
    const monthlyBoss = charBoss.filter(r => r.resetPeriod === 'MONTHLY')

    return HttpResponse.json({
      success: true,
      data: {
        characters: [
          {
            characterId: 1,
            characterName: '달빛제로',
            characterLevel: 285,
            characterClass: '제로',
            characterImage: null,
            worldName: '크로아',
            daily: {
              completed: charDaily.reduce((sum, r) => sum + r.completedCount, 0),
              total: charDaily.reduce((sum, r) => sum + r.totalCount, 0),
            },
            weekly: {
              completed: charWeekly.filter(r => r.isCompleted).length,
              total: charWeekly.length,
            },
            weeklyBoss: {
              completed: weeklyBoss.filter(r => r.isCompleted).length,
              total: weeklyBoss.length,
            },
            monthlyBoss: {
              completed: monthlyBoss.filter(r => r.isCompleted).length,
              total: monthlyBoss.length,
            },
          },
        ],
        syncedAt: new Date().toISOString(),
      },
    })
  }),
]
