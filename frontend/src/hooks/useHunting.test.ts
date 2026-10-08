import { describe, expect, it } from 'vitest'
import { buildHuntingQuery } from './useHunting'

describe('사냥 기록 조회 쿼리', () => {
  it('전체 즐겨찾기 조회에 서버 날짜 파라미터를 사용', () => {
    const query = new URLSearchParams(buildHuntingQuery({
      characterId: null,
      dateFrom: '2026-10-01',
      dateTo: '2026-10-06',
    }))

    expect(query.has('characterId')).toBe(false)
    expect(query.get('from')).toBe('2026-10-01')
    expect(query.get('to')).toBe('2026-10-06')
    expect(query.has('dateFrom')).toBe(false)
    expect(query.has('dateTo')).toBe(false)
  })

  it('선택한 즐겨찾기 캐릭터 ID를 포함', () => {
    expect(new URLSearchParams(buildHuntingQuery({ characterId: 7 })).get('characterId')).toBe('7')
  })
})
