import { http, HttpResponse } from 'msw'

const mockUser = {
  id: 1,
  oauthProvider: 'KAKAO' as const,
  oauthId: 'kakao_123456',
  email: 'mock@example.com',
  nickname: '달빛유저',
  createdAt: '2026-07-01T00:00:00',
  updatedAt: '2026-07-01T00:00:00',
}

const mockAuthToken = {
  accessToken: 'mock-access-token-xyz',
  tokenType: 'Bearer' as const,
  expiresIn: 3600,
}

export const authHandlers = [
  http.post('/api/v1/auth/kakao', () => {
    localStorage.setItem('accessToken', mockAuthToken.accessToken)
    return HttpResponse.json({
      success: true,
      data: { ...mockAuthToken, isNewUser: false, user: mockUser },
    })
  }),

  http.post('/api/v1/auth/nexon-apikey', () => {
    const nexonApiKeyUser = {
      ...mockUser,
      oauthProvider: 'NEXON_APIKEY' as const,
      oauthId: 'nexon_apikey_sha256_mock',
      email: null,
      nickname: '넥슨유저',
    }
    localStorage.setItem('accessToken', mockAuthToken.accessToken)
    return HttpResponse.json({
      success: true,
      data: { ...mockAuthToken, isNewUser: false, user: nexonApiKeyUser },
    })
  }),

  http.post('/api/v1/auth/refresh', () => {
    const refreshed = { ...mockAuthToken, accessToken: 'mock-refreshed-token-xyz' }
    localStorage.setItem('accessToken', refreshed.accessToken)
    return HttpResponse.json({ success: true, data: refreshed })
  }),

  http.post('/api/v1/auth/logout', () => {
    localStorage.removeItem('accessToken')
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/v1/users/me', () => {
    return HttpResponse.json({ success: true, data: mockUser })
  }),
]
