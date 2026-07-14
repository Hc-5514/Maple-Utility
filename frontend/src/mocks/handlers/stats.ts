import { http, HttpResponse } from 'msw'

const mesoTrend = Array.from({ length: 30 }, (_, i) => {
  const d = new Date('2026-07-14')
  d.setDate(d.getDate() - (29 - i))
  return {
    date: d.toISOString().split('T')[0],
    meso: Math.floor(280_000_000 + Math.random() * 200_000_000),
  }
})

const huntingTrend = Array.from({ length: 30 }, (_, i) => {
  const d = new Date('2026-07-14')
  d.setDate(d.getDate() - (29 - i))
  return {
    date: d.toISOString().split('T')[0],
    mesoEarned: Math.floor(280_000_000 + Math.random() * 200_000_000),
    solErdaEarned: Math.floor(2 + Math.random() * 5),
    playDurationMin: Math.floor(90 + Math.random() * 90),
  }
})

const bossStats = [
  { bossName: '스우',       difficulty: 'HARD',   clearCount: 4, totalCount: 4, clearRate: 100 },
  { bossName: '데미안',     difficulty: 'HARD',   clearCount: 4, totalCount: 4, clearRate: 100 },
  { bossName: '루시드',     difficulty: 'HARD',   clearCount: 3, totalCount: 4, clearRate: 75  },
  { bossName: '윌',         difficulty: 'HARD',   clearCount: 4, totalCount: 4, clearRate: 100 },
  { bossName: '더스크',     difficulty: 'CHAOS',  clearCount: 2, totalCount: 4, clearRate: 50  },
  { bossName: '칼로스',     difficulty: 'CHAOS',  clearCount: 3, totalCount: 4, clearRate: 75  },
  { bossName: '세렌',       difficulty: 'CHAOS',  clearCount: 1, totalCount: 4, clearRate: 25  },
  { bossName: '검은마법사', difficulty: 'NORMAL', clearCount: 2, totalCount: 4, clearRate: 50  },
]

export const statsHandlers = [
  http.get('/api/v1/stats/summary', () =>
    HttpResponse.json({
      success: true,
      data: {
        totalMeso: 11_400_000_000,
        bossesCleared: 23,
        avgPlayTime: 128,
        topHuntingGround: '모라스',
      },
    }),
  ),

  http.get('/api/v1/stats/meso', () =>
    HttpResponse.json({ success: true, data: mesoTrend }),
  ),

  http.get('/api/v1/stats/boss', () =>
    HttpResponse.json({ success: true, data: bossStats }),
  ),

  http.get('/api/v1/stats/hunting', () =>
    HttpResponse.json({ success: true, data: huntingTrend }),
  ),
]
