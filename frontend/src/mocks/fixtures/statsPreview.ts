import { acquisitionPeriod } from './statsItemPolicy'
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
  ['2026-10-15', 6.4], ['2026-10-22', 8.1], ['2026-10-29', 8.2], ['2026-11-05', 7.3],
  ['2026-09-03', 6.5], ['2026-09-10', 8.2], ['2026-09-17', 5.9], ['2026-09-24', 9.4],
]

export const previewCrystalWeeks = weeklyEok.flatMap(([weekStart, eok]) => {
  const total = Math.round(eok * 100_000_000)
  const first = Math.round(total * 0.6)
  const end = new Date(`${weekStart}T12:00:00`)
  end.setDate(end.getDate() + 6)
  const containsMonthStart = weekStart.endsWith('-01') || end.getDate() <= 6
  return [1, 2].flatMap((characterId) => {
    const income = characterId === 1 ? first : total - first
    const monthly = containsMonthStart ? Math.floor(income * 0.2) : 0
    return [
      { characterId, weekStart, bossName: '스우', difficulty: 'HARD' as BossDifficulty, income: income - monthly, resetPeriod: 'WEEKLY' as const },
      ...(monthly ? [{ characterId, weekStart, bossName: '검은 마법사', difficulty: 'HARD' as BossDifficulty, income: monthly, resetPeriod: 'MONTHLY' as const }] : []),
    ]
  })
})

const figmaRows: [string, number, string, BossDifficulty, string][] = [
  ['2026-09-30', 1, '검은 마법사', 'EXTREME', '창세의 뱃지'],
  ['2026-09-28', 2, '데미안', 'HARD', '마력이 깃든 안대'],
  ['2026-09-27', 1, '스우', 'HARD', '루즈 컨트롤 머신 마크'],
  ['2026-09-25', 2, '가엔슬', 'CHAOS', '가디언 엔젤 링'],
  ['2026-09-24', 1, '윌', 'HARD', '저주받은 마도서 선택 상자'],
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
  ['윌', 'HARD', '저주받은 마도서 선택 상자'],
  ['더스크', 'CHAOS', '거대한 공포'],
  ['진 힐라', 'HARD', '고통의 근원'],
]

// 2026-09 보스 아이템 획득 48건 (Figma 첫 페이지 10건 + 보충 38건)
export const septemberBossItems = (names: Record<number, string>): StatsBossItem[] => [
  ...figmaRows.map(([acquiredDate, characterId, bossName, difficulty, itemName]) => ({
    acquiredDate, characterName: names[characterId], bossName, difficulty, itemName,
    ...acquisitionPeriod(acquiredDate, bossName),
  })),
  ...Array.from({ length: 38 }, (_, index) => {
    const [bossName, difficulty, itemName] = fillerBosses[index % fillerBosses.length]
    return {
      acquiredDate: `2026-09-${String(9 - Math.floor(index / 5)).padStart(2, '0')}`,
      ...acquisitionPeriod(`2026-09-${String(9 - Math.floor(index / 5)).padStart(2, '0')}`, bossName),
      characterName: names[(index % 2) + 1],
      bossName,
      difficulty,
      itemName,
    }
  }),
]

// 승인 시안 확인용 합성 데이터. 운영 획득 기록과 무관.
export const novemberBossItems: StatsBossItem[] = [
  ['검은 마법사', 'EXTREME', '창세의 뱃지', null],
  ['데미안', 'HARD', '마력이 깃든 안대', null],
  ['스우', 'HARD', '루즈 컨트롤 머신 마크', null],
  ['최초의 대적자', 'HARD', '1단계 소울 에테르', null],
  ['윌', 'HARD', '저주받은 마도서 선택 상자', null],
  ['진 힐라', 'HARD', '고통의 근원', null],
  ['림보', 'HARD', '생명의 보스 반지 상자', '리스트레인트 링 Lv4'],
  ['더스크', 'CHAOS', '거대한 공포', null],
  ['듄켈', 'HARD', '커맨더 포스 이어링', null],
  ['발드릭스', 'HARD', '3단계 소울 에테르', null],
  ['루시드', 'HARD', '트와일라이트 마크', null],
  ['스우', 'HARD', '홍옥의 보스 반지 상자', '기타'],
  ['스우', 'HARD', '녹옥의 보스 반지 상자', null],
  ['림보', 'HARD', '생명의 보스 반지 상자', '컨티뉴어스링 Lv4'],
  ['카링', 'HARD', '2단계 소울 에테르', null],
  ['유피테르', 'HARD', '4단계 소울 에테르', null],
].map(([bossName, difficulty, itemName, detailItem], index) => ({
  acquiredDate: '2026-11-05',
  characterName: index % 2 ? '말랑꼬농' : '꼬농',
  bossName: bossName!, difficulty: difficulty as BossDifficulty,
  itemName: detailItem ? `${itemName} (${detailItem})` : itemName!,
  ...acquisitionPeriod('2026-11-05', bossName!), detailItem,
}))
