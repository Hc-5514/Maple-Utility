import { http, HttpResponse } from 'msw'
import { settingsFigmaScenario } from '../fixtures/settingsFigma'
import type { ApiKeyStatusResponse } from '../../types'

const mockApiKeyStatus: ApiKeyStatusResponse = {
  registered: true,
  keyStatus: 'ACTIVE',
  keySuffix: settingsFigmaScenario ? '9f2a' : null,
  lastVerifiedAt: settingsFigmaScenario ? '2026-09-17T09:42:00' : '2026-10-07T00:00:00',
}

export const apiKeyHandlers = [
  http.get('*/api/v1/api-key/status', () => {
    return HttpResponse.json({ success: true, data: mockApiKeyStatus })
  }),

  http.post('*/api/v1/api-key', async ({ request }) => {
    const { apiKey } = (await request.json()) as { apiKey: string }
    Object.assign(mockApiKeyStatus, {
      registered: true,
      keyStatus: 'ACTIVE',
      keySuffix: apiKey.slice(-4),
      lastVerifiedAt: new Date().toISOString(),
    })
    return HttpResponse.json({ success: true, data: mockApiKeyStatus }, { status: 201 })
  }),

  http.delete('*/api/v1/api-key', () => {
    Object.assign(mockApiKeyStatus, {
      registered: false,
      keyStatus: null,
      keySuffix: null,
      lastVerifiedAt: null,
    })
    return new HttpResponse(null, { status: 204 })
  }),
]
