// @vitest-environment jsdom
import axios, { AxiosError, isCancel, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import client from './client'
import { advanceSession } from './session'

const originalAdapter = client.defaults.adapter
const response = (config: InternalAxiosRequestConfig, data: unknown): AxiosResponse => ({
  data, config, status: 200, statusText: 'OK', headers: {},
})
const unauthorized = (config: InternalAxiosRequestConfig) => new AxiosError(
  'Unauthorized', 'ERR_BAD_REQUEST', config, undefined,
  { ...response(config, {}), status: 401 },
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
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('authentication session request isolation', () => {
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
