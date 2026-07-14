import { http, HttpResponse } from 'msw'
import characters from '../fixtures/characters.json'

let mockCharacters = [...characters]
let nextId = mockCharacters.length + 1

export const characterHandlers = [
  http.get('/api/v1/characters', () => {
    return HttpResponse.json({ success: true, data: mockCharacters })
  }),

  http.post('/api/v1/characters', async ({ request }) => {
    const body = (await request.json()) as { characterName: string; ocid?: string }
    const newChar = {
      id: nextId++,
      userId: 1,
      ocid: body.ocid ?? `ocid_new_${Date.now()}`,
      characterName: body.characterName,
      worldName: null as string | null,
      characterClass: null as string | null,
      characterLevel: null as number | null,
      characterImage: null,
      guildName: null as string | null,
      isFavorite: false,
      sortOrder: mockCharacters.length,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    mockCharacters.push(newChar as (typeof mockCharacters)[0])
    return HttpResponse.json({ success: true, data: newChar }, { status: 201 })
  }),

  http.get('/api/v1/characters/:id', ({ params }) => {
    const character = mockCharacters.find(c => c.id === Number(params.id))
    if (!character) {
      return HttpResponse.json({ success: false, message: '캐릭터를 찾을 수 없음' }, { status: 404 })
    }
    return HttpResponse.json({ success: true, data: character })
  }),

  http.put('/api/v1/characters/:id/favorite', ({ params }) => {
    const character = mockCharacters.find(c => c.id === Number(params.id))
    if (!character) {
      return HttpResponse.json({ success: false, message: '캐릭터를 찾을 수 없음' }, { status: 404 })
    }
    character.isFavorite = !character.isFavorite
    character.updatedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: character })
  }),

  http.delete('/api/v1/characters/:id', ({ params }) => {
    mockCharacters = mockCharacters.filter(c => c.id !== Number(params.id))
    return new HttpResponse(null, { status: 204 })
  }),
]
