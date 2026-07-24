import { http, HttpResponse } from 'msw'

const mockApiKeyStatus = {
  registered: true,
  keyStatus: 'ACTIVE' as const,
  lastVerifiedAt: '2026-07-14T08:00:00',
}

export const apiKeyHandlers = [
  http.get('/api/v1/api-key/status', () => {
    return HttpResponse.json({ success: true, data: mockApiKeyStatus })
  }),

  http.post('/api/v1/api-key', () => {
    return new HttpResponse(null, { status: 201 })
  }),

  http.delete('/api/v1/api-key', () => {
    return new HttpResponse(null, { status: 204 })
  }),
]
