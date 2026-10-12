import type { BossDifficulty, StatsBossItem } from '../../types'

// 보스 필터 목록 (boss_master sort_order 기준)
export const previewBossNames = [
  '자쿰', '매그너스', '반반', '피에르', '블러디 퀸', '벨룸', '파풀라투스', '스우', '데미안', '가엔슬',
  '루시드', '윌', '더스크', '듄켈', '진 힐라', '선택받은 세렌', '감시자 칼로스', '최초의 대적자', '카링',
  '찬란한 흉성', '벨로나', '림보', '발드릭스', '유피테르', '검은 마법사',
]

// 2026-07-09 ~ 2026-09-30 목요일 주차별 결정석 수익 (캐릭터 1·2 합계, 억 단위)
const weeklyEok: [string, number][] = [
  ['2026-07-09', 6.2], ['2026-07-16', 7.1], ['2026-07-23', 6.5], ['2026-07-30', 6.6],
  ['2026-08-06', 7.5], ['2026-08-13', 8.0], ['2026-08-20', 7.9], ['2026-08-27', 7.8],
  ['2026-09-03', 6.5], ['2026-09-10', 8.2], ['2026-09-17', 5.9], ['2026-09-24', 9.4],
]

export const previewCrystalWeeks = weeklyEok.flatMap(([weekStart, eok]) => {
  const total = Math.round(eok * 100_000_000)
  const first = Math.round(total * 0.6)
  return [
    { characterId: 1, weekStart, bossName: '검은 마법사', difficulty: 'HARD' as BossDifficulty, income: first },
    { characterId: 2, weekStart, bossName: '세렌', difficulty: 'HARD' as BossDifficulty, income: total - first },
  ]
})

const figmaRows: [string, number, string, BossDifficulty, string][] = [
  ['2026-09-30', 1, '검은 마법사', 'EXTREME', '창세의 배지'],
  ['2026-09-28', 2, '데미안', 'HARD', '마력이 깃든 안대'],
  ['2026-09-27', 1, '스우', 'HARD', '루즈 컨트롤 머신 마크'],
  ['2026-09-25', 2, '가엔슬', 'CHAOS', '가디언 엔젤 링'],
  ['2026-09-24', 1, '윌', 'HARD', '저주받은 적의 마도서'],
  ['2026-09-21', 2, '진 힐라', 'HARD', '고통의 근원'],
  ['2026-09-20', 1, '루시드', 'HARD', '몽환의 벨트'],
  ['2026-09-17', 2, '더스크', 'CHAOS', '거대한 공포'],
  ['2026-09-14', 1, '듄켈', 'HARD', '커맨더 포스 이어링'],
  ['2026-09-10', 2, '가엔슬', 'CHAOS', '가디언 엔젤 링'],
]
const fillerBosses: [string, BossDifficulty, string][] = [
  ['스우', 'HARD', '루즈 컨트롤 머신 마크'],
  ['데미안', 'HARD', '마력이 깃든 안대'],
  ['루시드', 'HARD', '몽환의 벨트'],
  ['윌', 'HARD', '저주받은 적의 마도서'],
  ['더스크', 'CHAOS', '거대한 공포'],
  ['진 힐라', 'HARD', '고통의 근원'],
]

// 2026-09 보스 아이템 획득 48건 (Figma 첫 페이지 10건 + 보충 38건)
export const septemberBossItems = (names: Record<number, string>): StatsBossItem[] => [
  ...figmaRows.map(([acquiredDate, characterId, bossName, difficulty, itemName]) => ({
    acquiredDate, characterName: names[characterId], bossName, difficulty, itemName,
  })),
  ...Array.from({ length: 38 }, (_, index) => {
    const [bossName, difficulty, itemName] = fillerBosses[index % fillerBosses.length]
    return {
      acquiredDate: `2026-09-${String(9 - Math.floor(index / 5)).padStart(2, '0')}`,
      characterName: names[(index % 2) + 1],
      bossName,
      difficulty,
      itemName,
    }
  }),
]
