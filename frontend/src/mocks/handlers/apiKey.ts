import { http, HttpResponse } from 'msw'

const mockApiKey = {
  id: 1,
  userId: 1,
  keyStatus: 'ACTIVE' as const,
  lastVerifiedAt: '2026-07-14T08:00:00',
  createdAt: '2026-07-01T00:00:00',
  updatedAt: '2026-07-14T08:00:00',
}

export const apiKeyHandlers = [
  http.get('/api/v1/user-api-keys', () => {
    return HttpResponse.json({ success: true, data: mockApiKey })
  }),

  http.post('/api/v1/user-api-keys', () => {
    return HttpResponse.json({ success: true, data: mockApiKey }, { status: 201 })
  }),

  http.delete('/api/v1/user-api-keys/:id', () => {
    return new HttpResponse(null, { status: 204 })
  }),
]
