import { http, HttpResponse } from 'msw'

const huntingTrend = Array.from({ length: 30 }, (_, i) => {
  const d = new Date('2026-07-14')
  d.setDate(d.getDate() - (29 - i))
  return {
    date: d.toISOString().split('T')[0],
    mesoEarned: Math.floor(280_000_000 + ((i * 7919) % 200_000_000)),
    solErdaEarned: 2 + (i % 5),
    playDurationMin: 90 + (i % 90) as number | null,
  }
})

const totalMeso = huntingTrend.reduce((s, r) => s + r.mesoEarned, 0)
const totalSolErda = huntingTrend.reduce((s, r) => s + r.solErdaEarned, 0)

const crystalSummary = {
  totalCrystalIncome: 9_800_000_000,
  weeklyAverage: 2_450_000_000,
  weeklyRecords: [
    {
      weekStart: '2026-06-23',
      totalIncome: 2_100_000_000,
      bossDetails: [
        { bossName: '스우',   difficulty: 'HARD',  income: 600_000_000 },
        { bossName: '데미안', difficulty: 'HARD',  income: 750_000_000 },
        { bossName: '루시드', difficulty: 'HARD',  income: 450_000_000 },
        { bossName: '더스크', difficulty: 'CHAOS', income: 300_000_000 },
      ],
    },
    {
      weekStart: '2026-06-30',
      totalIncome: 2_550_000_000,
      bossDetails: [
        { bossName: '스우',   difficulty: 'HARD',  income: 600_000_000 },
        { bossName: '데미안', difficulty: 'HARD',  income: 750_000_000 },
        { bossName: '루시드', difficulty: 'HARD',  income: 450_000_000 },
        { bossName: '칼로스', difficulty: 'CHAOS', income: 750_000_000 },
      ],
    },
    {
      weekStart: '2026-07-07',
      totalIncome: 2_300_000_000,
      bossDetails: [
        { bossName: '스우',   difficulty: 'HARD',  income: 600_000_000 },
        { bossName: '윌',     difficulty: 'HARD',  income: 500_000_000 },
        { bossName: '루시드', difficulty: 'HARD',  income: 450_000_000 },
        { bossName: '더스크', difficulty: 'CHAOS', income: 750_000_000 },
      ],
    },
    {
      weekStart: '2026-07-14',
      totalIncome: 2_850_000_000,
      bossDetails: [
        { bossName: '스우',   difficulty: 'HARD',  income: 600_000_000 },
        { bossName: '데미안', difficulty: 'HARD',  income: 750_000_000 },
        { bossName: '칼로스', difficulty: 'CHAOS', income: 750_000_000 },
        { bossName: '세렌',   difficulty: 'CHAOS', income: 750_000_000 },
      ],
    },
  ],
}

const bossItems = [
  { acquiredDate: '2026-07-14', characterName: '라이트닝브레이커', bossName: '더스크',   difficulty: 'HARD',  itemName: '아케인포스 장비' },
  { acquiredDate: '2026-07-13', characterName: '라이트닝브레이커', bossName: '루시드',   difficulty: 'HARD',  itemName: '앱솔랩스 장비' },
  { acquiredDate: '2026-07-11', characterName: '라이트닝브레이커', bossName: '스우',     difficulty: 'HARD',  itemName: '제네시스 장비' },
  { acquiredDate: '2026-07-09', characterName: '라이트닝브레이커', bossName: '칼로스',   difficulty: 'CHAOS', itemName: '아케인포스 장비' },
  { acquiredDate: '2026-07-06', characterName: '라이트닝브레이커', bossName: '데미안',   difficulty: 'HARD',  itemName: '제네시스 장비' },
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

  http.get('/api/v1/stats/hunting', () =>
    HttpResponse.json({
      success: true,
      data: {
        totalMeso,
        totalSolErda,
        avgDailyMeso: Math.floor(totalMeso / huntingTrend.length),
        avgDailySolErda: Math.floor(totalSolErda / huntingTrend.length),
        dailyRecords: huntingTrend,
      },
    }),
  ),

  http.get('/api/v1/stats/crystal', () =>
    HttpResponse.json({ success: true, data: crystalSummary }),
  ),

  http.get('/api/v1/stats/boss-items', () =>
    HttpResponse.json({ success: true, data: bossItems }),
  ),

  http.get('/api/v1/stats/meso', () =>
    HttpResponse.json({
      success: true,
      data: huntingTrend.map(r => ({ date: r.date, meso: r.mesoEarned })),
    }),
  ),

  http.get('/api/v1/stats/boss', () =>
    HttpResponse.json({
      success: true,
      data: [
        { bossName: '스우',       difficulty: 'HARD',   clearCount: 4, totalCount: 4, clearRate: 100 },
        { bossName: '데미안',     difficulty: 'HARD',   clearCount: 4, totalCount: 4, clearRate: 100 },
        { bossName: '루시드',     difficulty: 'HARD',   clearCount: 3, totalCount: 4, clearRate: 75  },
        { bossName: '윌',         difficulty: 'HARD',   clearCount: 4, totalCount: 4, clearRate: 100 },
        { bossName: '더스크',     difficulty: 'CHAOS',  clearCount: 2, totalCount: 4, clearRate: 50  },
        { bossName: '칼로스',     difficulty: 'CHAOS',  clearCount: 3, totalCount: 4, clearRate: 75  },
        { bossName: '세렌',       difficulty: 'CHAOS',  clearCount: 1, totalCount: 4, clearRate: 25  },
        { bossName: '검은마법사', difficulty: 'NORMAL', clearCount: 2, totalCount: 4, clearRate: 50  },
      ],
    }),
  ),
]
