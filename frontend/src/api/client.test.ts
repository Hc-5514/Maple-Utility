// @vitest-environment jsdom
import axios, { AxiosError, isCancel, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import client from './client'
import { advanceSession, getSessionGeneration } from './session'
import { useAuthStore } from '../stores/authStore'

const originalAdapter = client.defaults.adapter
const originalAuth = useAuthStore.getState()
const response = (config: InternalAxiosRequestConfig, data: unknown): AxiosResponse => ({
  data, config, status: 200, statusText: 'OK', headers: {},
})
const unauthorized = (config: InternalAxiosRequestConfig, code?: string) => new AxiosError(
  'Unauthorized', 'ERR_BAD_REQUEST', config, undefined,
  { ...response(config, code ? { code } : {}), status: 401 },
)

beforeEach(() => {
  const values = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  })
  advanceSession('previous-token')
})

afterEach(() => {
  client.defaults.adapter = originalAdapter
  useAuthStore.setState(originalAuth)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('authentication session request isolation', () => {
  it('preserves the token, identity and account caches on invalid new-key registration', async () => {
    const queryClient = new QueryClient()
    queryClient.setQueryData(['characters'], [{ id: 88 }])
    queryClient.setQueryData(['hunting'], [{ id: 99, mesoEarned: 500 }])
    useAuthStore.setState({
      user: { id: 8, nickname: '기존 계정', email: null, oauthProvider: 'KAKAO' },
      hasApiKey: true,
    })
    const previousAuth = useAuthStore.getState()
    const generation = getSessionGeneration()
    const refresh = vi.spyOn(axios, 'post')
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      throw unauthorized(config, 'API_KEY_INVALID')
    })
    client.defaults.adapter = adapter

    await expect(client.post('/api-key', { apiKey: 'invalid-new-key' })).rejects.toMatchObject({
      response: { status: 401, data: { code: 'API_KEY_INVALID' } },
    })

    expect(refresh).not.toHaveBeenCalled()
    expect(adapter).toHaveBeenCalledOnce()
    expect(localStorage.getItem('accessToken')).toBe('previous-token')
    expect(getSessionGeneration()).toBe(generation)
    expect(useAuthStore.getState()).toBe(previousAuth)
    expect(queryClient.getQueryData(['characters'])).toEqual([{ id: 88 }])
    expect(queryClient.getQueryData(['hunting'])).toEqual([{ id: 99, mesoEarned: 500 }])
    queryClient.clear()
  })

  it('refreshes an invalid JWT and retries key registration in the same session', async () => {
    const generation = getSessionGeneration()
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({ data: { accessToken: 'refreshed-token' } } as AxiosResponse)
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      if (config.headers.Authorization !== 'Bearer refreshed-token') throw unauthorized(config, 'INVALID_TOKEN')
      return response(config, { registered: true, keyStatus: 'ACTIVE' })
    })
    client.defaults.adapter = adapter

    const registered = await client.post('/api-key', { apiKey: 'valid-new-key' })

    expect(registered.data.data).toEqual({ registered: true, keyStatus: 'ACTIVE' })
    expect(refresh).toHaveBeenCalledOnce()
    expect(adapter).toHaveBeenCalledTimes(2)
    expect(localStorage.getItem('accessToken')).toBe('refreshed-token')
    expect(getSessionGeneration()).toBe(generation)
  })

  it('discards an old successful response after changing the data context', async () => {
    let complete!: () => void
    const started = vi.fn()
    client.defaults.adapter = (config) => new Promise((resolve) => {
      started()
      complete = () => resolve(response(config, ['old-account-character']))
    })
    const pending = client.get('/characters').catch((error: unknown) => error)
    await vi.waitFor(() => expect(started).toHaveBeenCalled())

    advanceSession('new-context-token')
    complete()

    expect(isCancel(await pending)).toBe(true)
    expect(localStorage.getItem('accessToken')).toBe('new-context-token')
  })

  it('does not refresh or replay a 401 from the old context', async () => {
    let complete!: () => void
    const started = vi.fn()
    const refresh = vi.spyOn(axios, 'post')
    client.defaults.adapter = (config) => new Promise((_resolve, reject) => {
      started()
      complete = () => reject(unauthorized(config))
    })
    const pending = client.get('/characters').catch((error: unknown) => error)
    await vi.waitFor(() => expect(started).toHaveBeenCalled())

    advanceSession('new-context-token')
    complete()

    expect(isCancel(await pending)).toBe(true)
    expect(refresh).not.toHaveBeenCalled()
  })

  it.each(['success', 'failure'] as const)('ignores an old refresh %s after a new login', async (outcome) => {
    let resolveRefresh!: (value: AxiosResponse) => void
    let rejectRefresh!: (error: Error) => void
    const refresh = vi.spyOn(axios, 'post').mockImplementation(() => new Promise((resolve, reject) => {
      resolveRefresh = resolve
      rejectRefresh = reject
    }))
    client.defaults.adapter = async (config) => { throw unauthorized(config) }
    const pending = client.get('/characters').catch((error: unknown) => error)
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())

    advanceSession('new-login-token')
    if (outcome === 'success') resolveRefresh({ data: { accessToken: 'old-refresh-token' } } as AxiosResponse)
    else rejectRefresh(new Error('old refresh expired'))

    expect(isCancel(await pending)).toBe(true)
    expect(localStorage.getItem('accessToken')).toBe('new-login-token')
  })

  it('shares one refresh and retries both requests within the current session', async () => {
    let complete!: () => void
    const refresh = vi.spyOn(axios, 'post').mockImplementation(() => new Promise((resolve) => {
      complete = () => resolve({ data: { accessToken: 'refreshed-token' } } as AxiosResponse)
    }))
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      if (config.headers.Authorization !== 'Bearer refreshed-token') throw unauthorized(config)
      return response(config, { currentAccount: true })
    })
    client.defaults.adapter = adapter
    const pending = Promise.all([client.get('/characters'), client.get('/stats/hunting')])
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(2))
    expect(refresh).toHaveBeenCalledOnce()
    complete()

    expect((await pending).every((result) => result.data.data.currentAccount)).toBe(true)
    expect(adapter).toHaveBeenCalledTimes(4)
  })

  it('returns login failures without using a previous refresh cookie', async () => {
    const refresh = vi.spyOn(axios, 'post')
    client.defaults.adapter = async (config) => { throw unauthorized(config) }

    await expect(client.post('/auth/nexon-apikey', { apiKey: 'invalid' })).rejects.toMatchObject({ response: { status: 401 } })
    expect(refresh).not.toHaveBeenCalled()
    expect(localStorage.getItem('accessToken')).toBe('previous-token')
  })
})
