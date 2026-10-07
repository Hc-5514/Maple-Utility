import { http, HttpResponse } from 'msw'
import { previewCharacters } from '../fixtures/nexonPreview'

const mockCharacters = previewCharacters.map((character) => ({ ...character }))

export const characterHandlers = [
  http.get('*/api/v1/characters/favorites', () => {
    return HttpResponse.json({ success: true, data: mockCharacters.filter(c => c.favorite).sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id) })
  }),
  http.get('*/api/v1/characters', () => {
    return HttpResponse.json({ success: true, data: mockCharacters })
  }),

  http.patch('*/api/v1/characters/:id/favorite', ({ params }) => {
    const character = mockCharacters.find(c => c.id === Number(params.id))
    if (!character) {
      return HttpResponse.json({ success: false, message: '캐릭터를 찾을 수 없음' }, { status: 404 })
    }
    character.favorite = !character.favorite
    character.updatedAt = new Date().toISOString()
    return HttpResponse.json({ success: true, data: character })
  }),

  http.post('*/api/v1/characters/sync', () => {
    return HttpResponse.json(
      { success: true, data: { id: 1, jobType: 'CHARACTER', status: 'STARTED', totalCount: mockCharacters.length, completedCount: 0, skippedCount: 0, errorMessage: null, startedAt: new Date().toISOString(), completedAt: null } },
      { status: 200 },
    )
  }),

  http.get('*/api/v1/characters/sync-jobs/:jobId', () => {
    return HttpResponse.json({ success: true, data: { id: 1, jobType: 'CHARACTER', status: 'COMPLETED', totalCount: mockCharacters.length, completedCount: mockCharacters.length, skippedCount: 0, errorMessage: null, startedAt: new Date().toISOString(), completedAt: new Date().toISOString() } })
  }),
]
