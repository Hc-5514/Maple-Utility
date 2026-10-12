import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { setupServer } from 'msw/node'
import { characterHandlers } from './character'
import type { Character } from '../../types'
const server = setupServer(...characterHandlers)
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterAll(() => server.close())
const url = 'http://localhost/api/v1/characters'
async function characters(): Promise<Character[]> {
  return (await (await fetch(url)).json()).data
}
const save = (body: unknown) =>
  fetch(`${url}/preferences`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
describe('character preference mock contract', () => {
  it('persists the requested complete order with one-based sort values', async () => {
    const current = await characters()
    const preferences = [...current]
      .reverse()
      .map((character) => ({ characterId: character.id, favorite: character.favorite }))
    const response = await save({ characters: preferences })
    expect(response.status).toBe(200)
    const saved = (await response.json()).data as Character[]
    expect(saved.map((character) => character.id)).toEqual(
      preferences.map((character) => character.characterId)
    )
    expect(saved.map((character) => character.sortOrder)).toEqual(
      saved.map((_, index) => index + 1)
    )
    expect(await characters()).toEqual(saved)
  })
  it('rejects omitted owned characters as a list conflict without partial updates', async () => {
    const before = await characters()
    expect((await save({ characters: [] })).status).toBe(409)
    expect(await characters()).toEqual(before)
  })
  it('rejects malformed, null and duplicate preferences as bad requests', async () => {
    const before = await characters()
    const first = { characterId: before[0].id, favorite: true }
    for (const body of [
      null,
      {},
      { characters: [null] },
      { characters: [first, first] },
      { characters: [{ characterId: -1, favorite: true }] },
    ]) {
      expect((await save(body)).status).toBe(400)
    }
    expect(await characters()).toEqual(before)
  })
})
