import { describe, expect, it } from 'vitest'
import { buildHuntingQuery } from './useHunting'

describe('hunting list query', () => {
  it('omits characterId for the favorite aggregate and uses server date names', () => {
    const query = new URLSearchParams(buildHuntingQuery({
      characterId: null,
      dateFrom: '2026-10-01',
      dateTo: '2026-10-06',
    }))

    expect(query.has('characterId')).toBe(false)
    expect(query.get('from')).toBe('2026-10-01')
    expect(query.get('to')).toBe('2026-10-06')
    expect(query.has('dateFrom')).toBe(false)
  })

  it('selects one favorite when requested', () => {
    expect(new URLSearchParams(buildHuntingQuery({ characterId: 7 })).get('characterId')).toBe('7')
  })
})
