// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import client from '../api/client'
import AuthCallbackPage from './AuthCallbackPage'

const response = (data: unknown) => ({ data: { success: true, data } }) as AxiosResponse

function renderCallback(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/auth/callback" element={<AuthCallbackPage />} />
        <Route path="/dashboard" element={<p>대시보드 화면</p>} />
        <Route path="/settings" element={<p>설정 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

function mockGet(apiKeyStatus: 'ACTIVE' | 'NONE' | 'ERROR') {
  return vi.spyOn(client, 'get').mockImplementation(async (url) => {
    if (url === '/auth/me') return response({ id: 1, nickname: '메이플' })
    if (apiKeyStatus === 'ERROR') throw new Error('status failed')
    return response({ registered: apiKeyStatus === 'ACTIVE', keyStatus: apiKeyStatus === 'ACTIVE' ? 'ACTIVE' : null })
  })
}

// Node 내장 Web Storage가 jsdom 저장소를 가리는 환경 대비 메모리 저장소 사용
function memoryStorage(): Storage {
  const items = new Map<string, string>()
  return {
    get length() { return items.size },
    clear: () => items.clear(),
    getItem: (key) => items.get(key) ?? null,
    key: (index) => [...items.keys()][index] ?? null,
    removeItem: (key) => { items.delete(key) },
    setItem: (key, value) => { items.set(key, String(value)) },
  }
}

beforeEach(() => {
  vi.stubGlobal('localStorage', memoryStorage())
  vi.stubGlobal('sessionStorage', memoryStorage())
  sessionStorage.setItem('oauth_provider', 'kakao')
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('AuthCallbackPage', () => {
  it('shows the missing-code error without calling the API', async () => {
    const post = vi.spyOn(client, 'post')
    renderCallback('/auth/callback')

    expect(screen.getByText('인가코드를 받지 못했습니다.')).toBeTruthy()
    expect(screen.getByRole('button', { name: '로그인 페이지로' })).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    await waitFor(() => expect(sessionStorage.getItem('oauth_provider')).toBeNull())
  })

  it('logs in and moves to the dashboard when the API key is active', async () => {
    const post = vi.spyOn(client, 'post').mockResolvedValue(response({ accessToken: 'token' }))
    const get = mockGet('ACTIVE')
    renderCallback('/auth/callback?code=abc')

    expect(await screen.findByText('대시보드 화면')).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/auth/kakao', { code: 'abc' })
    expect(get).toHaveBeenCalledWith('/auth/me')
    expect(get).toHaveBeenCalledWith('/api-key/status')
    expect(localStorage.getItem('accessToken')).toBe('token')
  })

  it.each(['NONE', 'ERROR'] as const)('moves to settings when the API key status is %s', async (status) => {
    vi.spyOn(client, 'post').mockResolvedValue(response({ accessToken: 'token' }))
    mockGet(status)
    renderCallback('/auth/callback?code=abc')

    expect(await screen.findByText('설정 화면')).toBeTruthy()
  })

  it('shows the login error when the code exchange fails', async () => {
    vi.spyOn(client, 'post').mockRejectedValue(new Error('invalid code'))
    renderCallback('/auth/callback?code=abc')

    expect(await screen.findByText('로그인 중 오류가 발생했습니다.')).toBeTruthy()
  })
})
