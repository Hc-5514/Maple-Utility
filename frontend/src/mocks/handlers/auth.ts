import { http, HttpResponse } from 'msw'

const mockUser = {
  id: 1,
  oauthProvider: 'KAKAO' as const,
  email: 'mock@example.com',
  nickname: '미리보기',
}

export const mockAuthToken = {
  accessToken: 'mock-access-token-xyz',
  tokenType: 'Bearer' as const,
  expiresIn: 3600,
}

export const authHandlers = [
  http.post('*/api/v1/auth/kakao', () => {
    return HttpResponse.json({
      success: true,
      data: {
        ...mockAuthToken,
        user: { id: 1, nickname: '미리보기', email: 'mock@example.com', isNewUser: false },
      },
    })
  }),

  http.post('*/api/v1/auth/nexon-apikey', () => {
    return HttpResponse.json({
      success: true,
      data: {
        ...mockAuthToken,
        user: { id: 1, nickname: '넥슨유저', email: null, isNewUser: false },
      },
    })
  }),

  http.post('*/api/v1/auth/refresh', () => {
    const refreshed = { ...mockAuthToken, accessToken: 'mock-refreshed-token-xyz' }
    return HttpResponse.json({ success: true, data: refreshed })
  }),

  http.post('*/api/v1/auth/logout', () => {
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/v1/auth/me', () => {
    return HttpResponse.json({ success: true, data: mockUser })
  }),
]
