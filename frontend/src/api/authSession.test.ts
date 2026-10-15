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
  it('reloads shared favorites after key login, key switch, logout and Kakao login', async () => {
    const savedFavorites = [{ id: 31, favorite: true, sortOrder: 2 }]
    const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
      if (url === '/auth/me') return response({
        ...user,
        oauthProvider: localStorage.getItem('accessToken') === 'kakao-token' ? 'KAKAO' : 'NEXON_APIKEY',
      })
      if (url === '/api-key/status') return response({ registered: true, keyStatus: 'ACTIVE' })
      if (url === '/characters/favorites') return response(savedFavorites)
      throw new Error(`Unexpected test GET: ${url}`)
    })
    const loadFavorites = () => queryClient.fetchQuery({
      queryKey: ['characters/favorites'],
      staleTime: Infinity,
      queryFn: async () => (await client.get('/characters/favorites')).data.data,
    })

    await installAuthSession(queryClient, { ...auth, accessToken: 'key-a-token' }, 'NEXON_APIKEY')
    expect(await loadFavorites()).toEqual(savedFavorites)
    // A second key for the same verified account must refetch instead of retaining stale cache.
    queryClient.setQueryData(['characters/favorites'], [{ id: 99, favorite: false }])
    await installAuthSession(queryClient, { ...auth, accessToken: 'key-b-token' }, 'NEXON_APIKEY')
    expect(await loadFavorites()).toEqual(savedFavorites)
    await clearAuthSession(queryClient)
    expect(queryClient.getQueryCache().getAll()).toEqual([])
    expect(localStorage.getItem('accessToken')).toBeNull()
    await installAuthSession(queryClient, { ...auth, accessToken: 'kakao-token' }, 'KAKAO')

    expect(await loadFavorites()).toEqual(savedFavorites)
    expect(get.mock.calls.filter(([url]) => url === '/characters/favorites')).toHaveLength(3)
    expect(useAuthStore.getState()).toMatchObject({ user: { oauthProvider: 'KAKAO' }, hasApiKey: true })
  })

  it('does not restore active key state from a status response arriving after logout', async () => {
    let completeStatus!: () => void
    const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
      if (url === '/auth/me') return response(user)
      return new Promise<AxiosResponse>((resolve) => {
        completeStatus = () => resolve(response({ registered: true, keyStatus: 'ACTIVE' }))
      })
    })
    const pending = installAuthSession(queryClient, auth).catch((error: unknown) => error)
    await vi.waitFor(() => expect(get).toHaveBeenCalledWith('/api-key/status'))

    await clearAuthSession(queryClient)
    completeStatus()

    expect(isCancel(await pending)).toBe(true)
    expect(useAuthStore.getState()).toMatchObject({ user: null, hasApiKey: false, isInitializing: false })
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(queryClient.getQueryCache().getAll()).toEqual([])
  })

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
