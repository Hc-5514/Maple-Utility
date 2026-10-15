import { http, HttpResponse } from 'msw'
import { settingsFigmaScenario } from '../fixtures/settingsFigma'
import type { ApiKeyStatusResponse } from '../../types'
import { mockAuthToken } from './auth'
import { MAX_MOCK_ACCOUNTS, accountCount, addAccount, mockFailure, precheck } from './dataContextStore'

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
    // 다중 계정 시나리오: 사전 확인과 같은 규칙으로 중복·한도·실패를 재현
    const status = precheck(apiKey)
    if (status === 'LINKED_TO_OTHER_KAKAO') {
      return HttpResponse.json(
        { code: 'DATA_CONTEXT_CONFLICT', message: '이미 다른 카카오 계정에 연결된 데이터셋' },
        { status: 409 }
      )
    }
    if (status === 'LIMIT_EXCEEDED') {
      return HttpResponse.json(
        {
          code: 'DATA_CONTEXT_LIMIT_EXCEEDED',
          message: `넥슨 계정은 최대 ${MAX_MOCK_ACCOUNTS}개까지 연결할 수 있습니다`,
        },
        { status: 409 }
      )
    }
    if (mockFailure === 'add') {
      return HttpResponse.json({ code: 'INTERNAL_ERROR', message: '계정 추가 실패' }, { status: 500 })
    }
    if (status === 'AVAILABLE' && accountCount() > 0) addAccount(apiKey)
    Object.assign(mockApiKeyStatus, {
      registered: true,
      keyStatus: 'ACTIVE',
      keySuffix: apiKey.slice(-4),
      lastVerifiedAt: new Date().toISOString(),
    })
    return HttpResponse.json({ success: true, data: {
      ...mockApiKeyStatus,
      auth: {
        ...mockAuthToken,
        user: { id: 1, nickname: '미리보기', email: 'mock@example.com', isNewUser: false },
      },
    } }, { status: 201 })
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
