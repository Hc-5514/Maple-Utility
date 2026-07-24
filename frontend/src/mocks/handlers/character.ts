import { http, HttpResponse } from 'msw'
import characters from '../fixtures/characters.json'

let mockCharacters = [...characters]

export const characterHandlers = [
  http.get('/api/v1/characters', () => {
    return HttpResponse.json({ success: true, data: mockCharacters })
  }),

  http.patch('/api/v1/characters/:id/favorite', ({ params }) => {
    const character = mockCharacters.find(c => c.id === Number(params.id))
    if (!character) {
      return HttpResponse.json({ success: false, message: '캐릭터를 찾을 수 없음' }, { status: 404 })
    }
    character.favorite = !character.favorite
    character.updatedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: character })
  }),

  http.post('/api/v1/characters/sync', () => {
    return HttpResponse.json(
      { success: true, data: mockCharacters, message: '캐릭터 동기화 완료' },
      { status: 200 },
    )
  }),
]
