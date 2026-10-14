import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { setupServer } from 'msw/node'
import type { Character } from '../../types'

vi.mock('../fixtures/settingsFigma', async (importOriginal) => {
  const original = await importOriginal<typeof import('../fixtures/settingsFigma')>()
  return {
    ...original,
    settingsFigmaScenario: true,
    settingsPreviewCharacters: Array.from({ length: 20 }, (_, index) => ({
      ...original.settingsFigmaCharacters[0],
      id: index + 1,
      characterName: `캐릭터${index + 1}`,
      worldName: index % 2 === 0 ? '베라' : '엘리시움',
      favorite: index < 19,
      sortOrder: index + 1,
    })),
  }
})

const { characterHandlers } = await import('./character')
const server = setupServer(...characterHandlers)
const url = 'http://localhost/api/v1/characters'
const list = async (): Promise<Character[]> => (await (await fetch(url)).json()).data
const save = (characters: Character[], favoriteCount: number) =>
  fetch(`${url}/preferences`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      characters: characters.map((character, index) => ({
        characterId: character.id,
        favorite: index < favoriteCount,
      })),
    }),
  })
const toggle = (id: number) => fetch(`${url}/${id}/favorite`, { method: 'PATCH' })

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterAll(() => server.close())

describe('character favorite-limit mock contract', () => {
  it('preserves existing excess favorites and permits removal', async () => {
    expect((await list()).filter((character) => character.favorite)).toHaveLength(19)
    expect((await toggle(20)).status).toBe(400)
    expect((await toggle(1)).status).toBe(200)
    expect((await list()).filter((character) => character.favorite)).toHaveLength(18)
  })
})

describe('character favorite-limit writes', () => {
  beforeEach(async () => {
    expect((await save(await list(), 17)).status).toBe(200)
  })

  it('allows the eighteenth favorite across worlds and blocks the nineteenth', async () => {
    expect((await toggle(18)).status).toBe(200)
    const response = await toggle(19)
    expect(response.status).toBe(400)
    expect((await response.json()).code).toBe('FAVORITE_LIMIT_EXCEEDED')
    expect((await list()).filter((character) => character.favorite)).toHaveLength(18)
    expect((await toggle(1)).status).toBe(200)
    expect((await toggle(19)).status).toBe(200)
  })

  it('rejects over-limit bulk changes without partially saving favorites or order', async () => {
    const before = await list()
    const response = await save([...before].reverse(), 19)
    expect(response.status).toBe(400)
    expect((await response.json()).code).toBe('FAVORITE_LIMIT_EXCEEDED')
    expect(await list()).toEqual(before)
    expect((await save(before, 18)).status).toBe(200)
  })
})
