// @vitest-environment jsdom
import { QueryClient } from '@tanstack/react-query'
import { isCancel, type AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import client from './client'
import { clearAuthSession, installAuthSession } from './authSession'
import { useAuthStore } from '../stores/authStore'
import { useCharacterStore } from '../stores/characterStore'
import type { AuthLoginResponse, User } from '../types'

const user: User = { id: 8, oauthProvider: 'KAKAO', nickname: '연결 계정', email: null }
const auth: AuthLoginResponse = {
  accessToken: 'new-context-token', tokenType: 'Bearer', expiresIn: 1800,
  user: { ...user, nickname: '연결 계정', isNewUser: false },
}
const response = (data: unknown) => ({ data: { success: true, data } }) as AxiosResponse
let queryClient: QueryClient

beforeEach(() => {
  const values = new Map<string, string>([['accessToken', 'previous-token']])
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  })
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  useAuthStore.setState({ user, hasApiKey: true, isInitializing: false })
})

afterEach(() => {
  queryClient.clear()
  useAuthStore.setState({ user: null, hasApiKey: false, isInitializing: false })
  useCharacterStore.getState().setCharacters([])
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('login and data context session installation', () => {
  it('clears all previous account caches before loading identity and key status', async () => {
    queryClient.setQueryData(['hunting', 3], ['old-record'])
    queryClient.setQueryData(['stats/crystal'], { income: 900 })
    queryClient.setQueryData(['characters'], ['old-character'])
    const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
      expect(queryClient.getQueryData(['hunting', 3])).toBeUndefined()
      expect(queryClient.getQueryData(['stats/crystal'])).toBeUndefined()
      expect(useCharacterStore.getState().characters).toEqual([])
      expect(localStorage.getItem('accessToken')).toBe(auth.accessToken)
      return response(url === '/auth/me' ? user : { registered: true, keyStatus: 'ACTIVE' })
    })

    expect(await installAuthSession(queryClient, auth)).toBe(true)
    expect(get).toHaveBeenCalledWith('/auth/me')
    expect(get).toHaveBeenCalledWith('/api-key/status')
    expect(useAuthStore.getState()).toMatchObject({ user, hasApiKey: true, isInitializing: false })
  })

  it('clears a previous active-key flag when the new login has no key', async () => {
    vi.spyOn(client, 'get').mockImplementation(async (url) => response(
      url === '/auth/me' ? user : { registered: false, keyStatus: null },
    ))

    expect(await installAuthSession(queryClient, auth)).toBe(false)
    expect(useAuthStore.getState().hasApiKey).toBe(false)
  })

  it('does not reinstall identity from a canceled login after logout', async () => {
    let complete!: () => void
    const get = vi.spyOn(client, 'get').mockImplementation(() => new Promise((resolve) => {
      complete = () => resolve(response(user))
    }))
    const pending = installAuthSession(queryClient, auth).catch((error: unknown) => error)
    await vi.waitFor(() => expect(get).toHaveBeenCalledOnce())

    await clearAuthSession(queryClient)
    complete()

    expect(isCancel(await pending)).toBe(true)
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(useAuthStore.getState()).toMatchObject({ user: null, hasApiKey: false })
    expect(queryClient.getQueryCache().getAll()).toEqual([])
  })
})
