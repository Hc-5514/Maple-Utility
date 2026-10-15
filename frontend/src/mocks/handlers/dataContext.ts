import { http, HttpResponse } from 'msw'
import { mockAuthToken } from './auth'
import {
  MAX_MOCK_ACCOUNTS,
  accountCount,
  listItems,
  mockAccountScenario,
  mockFailure,
  precheck,
  removeAccount,
  selectCredential,
} from './dataContextStore'

const authBody = () => ({
  ...mockAuthToken,
  user: { id: 1, nickname: '미리보기', email: 'mock@example.com', isNewUser: false },
})

const serverError = (code: string, message: string) =>
  HttpResponse.json({ code, message }, { status: 500 })

export const dataContextHandlers = [
  http.get('*/api/v1/auth/data-contexts', () => HttpResponse.json(listItems())),

  http.post('*/api/v1/auth/data-context', async ({ request }) => {
    const { credentialId } = (await request.json()) as { credentialId: number }
    if (!selectCredential(credentialId)) {
      return HttpResponse.json(
        { code: 'DATA_CONTEXT_ACCESS_DENIED', message: '데이터셋 접근 권한 없음' },
        { status: 401 }
      )
    }
    return HttpResponse.json(authBody())
  }),

  // 연동 사전 확인: 데이터를 바꾸지 않는다
  http.post('*/api/v1/auth/data-contexts/precheck', async ({ request }) => {
    if (mockFailure === 'precheck') return serverError('INTERNAL_ERROR', '확인 실패')
    const { apiKey } = (await request.json()) as { apiKey: string }
    return HttpResponse.json({
      status: precheck(apiKey),
      connectedCount: accountCount(),
      maxCount: MAX_MOCK_ACCOUNTS,
    })
  }),

  // 연결 해제: 기록 데이터는 보존하고 세션을 남은 계정으로 재발급
  http.delete('*/api/v1/auth/data-contexts/:contextId/link', ({ params }) => {
    if (mockFailure === 'unlink') return serverError('INTERNAL_ERROR', '연결 해제 실패')
    if (mockAccountScenario === 'apikey') {
      return HttpResponse.json(
        { code: 'DATA_CONTEXT_UNLINK_NOT_ALLOWED', message: '카카오 로그인에서만 연결 해제 가능' },
        { status: 403 }
      )
    }
    const { removed } = removeAccount(Number(params.contextId))
    if (!removed) {
      return HttpResponse.json(
        { code: 'DATA_CONTEXT_ACCESS_DENIED', message: '데이터셋 접근 권한 없음' },
        { status: 401 }
      )
    }
    return HttpResponse.json(authBody())
  }),

  // 데이터 영구 삭제: API Key 로그인은 계정 자체가 삭제되어 204(로그아웃)
  http.delete('*/api/v1/auth/data-contexts/:contextId', ({ params }) => {
    if (mockFailure === 'delete') return serverError('INTERNAL_ERROR', '삭제 실패')
    const { removed } = removeAccount(Number(params.contextId))
    if (!removed) {
      return HttpResponse.json(
        { code: 'DATA_CONTEXT_ACCESS_DENIED', message: '데이터셋 접근 권한 없음' },
        { status: 401 }
      )
    }
    if (mockAccountScenario === 'apikey') return new HttpResponse(null, { status: 204 })
    return HttpResponse.json(authBody())
  }),
]
