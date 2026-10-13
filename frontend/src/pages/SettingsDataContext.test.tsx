// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosResponse } from 'axios'
import client from '../api/client'
import { getSessionGeneration } from '../api/session'
import Layout from '../components/layout/Layout'
import { groupDataContexts } from '../hooks/useDataContexts'
import { useAuthStore } from '../stores/authStore'
import { useCharacterStore } from '../stores/characterStore'
import { settingsFigmaCharacters } from '../mocks/fixtures/settingsFigma'
import type { ApiKeyStatusResponse, DataContextItem } from '../types'
import SettingsPage from './SettingsPage'

const contexts: DataContextItem[] = [
  {
    contextId: 1,
    ownerUserId: 1,
    credentialId: 10,
    keySuffix: '9f2a',
    status: 'ACTIVE',
    current: true,
    lastVerifiedAt: '2026-09-17T09:42:00',
    representativeCharacterName: '꼬농',
    characterClass: '아크메이지(썬,콜)',
    worldName: '엘리시움',
    characterLevel: 294,
  },
  {
    contextId: 1,
    ownerUserId: 1,
    credentialId: 11,
    keySuffix: '1b3c',
    status: 'EXPIRED',
    current: false,
    lastVerifiedAt: '2026-08-30T21:15:00',
    representativeCharacterName: '꼬농',
    characterClass: '아크메이지(썬,콜)',
    worldName: '엘리시움',
    characterLevel: 294,
  },
  {
    contextId: 2,
    ownerUserId: 2,
    credentialId: 20,
    keySuffix: '77d0',
    status: 'ACTIVE',
    current: false,
    lastVerifiedAt: '2026-10-02T18:20:00',
    representativeCharacterName: '꼬마별궁수',
    characterClass: '보우마스터',
    worldName: '엘리시움',
    characterLevel: 260,
  },
]
const character = { ...settingsFigmaCharacters[0], favorite: false, sortOrder: 1 }
const status: ApiKeyStatusResponse = {
  registered: true,
  keySuffix: '9f2a',
  keyStatus: 'ACTIVE',
  lastVerifiedAt: '2026-09-17T09:42:00',
}
const switched = {
  accessToken: 'switched-token',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { id: 1, nickname: '테스트', email: null, isNewUser: false },
}
const initialAuth = useAuthStore.getState()
const initialCharacters = useCharacterStore.getState()
const NativeRequest = Request
const queryClients: QueryClient[] = []

function ok<T>(data: T) {
  return { data: { success: true, data } } as AxiosResponse
}
function failure(code: string) {
  return new AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config: {} as never,
    data: { code },
  } as AxiosResponse)
}

function setup(options: { switchError?: string; listError?: boolean } = {}) {
  const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
    if (url === '/auth/data-contexts') {
      if (options.listError) throw new Error('network')
      return ok(contexts)
    }
    if (url === '/characters') return ok([character])
    if (url === '/characters/world-selection') return ok({ worldNames: ['엘리시움'] })
    if (url === '/api-key/status') return ok(status)
    if (url === '/auth/me') return ok({ id: 1, nickname: '테스트', email: null })
    return ok(null)
  })
  const post = vi.spyOn(client, 'post').mockImplementation(async () => {
    if (options.switchError) throw failure(options.switchError)
    return ok(switched)
  })
  const router = createMemoryRouter(
    [{ element: <Layout />, children: [{ path: '/settings', element: <SettingsPage /> }] }],
    { initialEntries: ['/settings'] }
  )
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  queryClients.push(queryClient)
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
  return { get, post }
}

const card = () => within(screen.getByTestId('data-context-card'))

beforeEach(() => {
  vi.stubGlobal(
    'Request',
    class extends NativeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        super(input, { ...init, signal: undefined })
      }
    }
  )
  const items = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => items.get(key) ?? null,
    setItem: (key: string, value: string) => items.set(key, value),
    removeItem: (key: string) => items.delete(key),
    clear: () => items.clear(),
  })
  useAuthStore.setState({
    user: { id: 1, nickname: '테스트', email: null, oauthProvider: 'KAKAO' },
    hasApiKey: true,
    isInitializing: false,
  })
  localStorage.setItem('accessToken', 'test-token')
})

afterEach(() => {
  cleanup()
  queryClients.splice(0).forEach((queryClient) => queryClient.clear())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  useAuthStore.setState(initialAuth)
  useCharacterStore.setState(initialCharacters)
})

describe('Settings connected Nexon accounts', () => {
  it('groups keys by account, marks the current session and shares a key limit note', async () => {
    setup()
    await card().findByText('● 현재 사용 중')
    const groups = card().getAllByTestId('data-context-group')
    expect(groups).toHaveLength(2)
    expect(within(groups[0]).getByText('꼬농')).toBeTruthy()
    expect(within(groups[0]).getByText('현재 세션')).toBeTruthy()
    expect(within(groups[0]).getByText('••••9f2a')).toBeTruthy()
    expect(within(groups[0]).getByText('••••1b3c')).toBeTruthy()
    expect(within(groups[0]).getByText('● EXPIRED')).toBeTruthy()
    expect(within(groups[0]).getByText(/API Key 2\/3개/)).toBeTruthy()
    expect(within(groups[1]).getByText('꼬마별궁수')).toBeTruthy()
    expect(within(groups[1]).getByRole('button', { name: '꼬마별궁수 계정으로 전환' })).toBeTruthy()
    expect(card().getAllByTestId('data-context-key')).toHaveLength(3)
  })

  it('switches the session after confirmation and replaces the access token', async () => {
    const { post } = setup()
    const generation = getSessionGeneration()
    fireEvent.click(await card().findByRole('button', { name: '꼬마별궁수 계정으로 전환' }))
    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getByText(/현재 화면의 데이터가 선택한 넥슨 계정/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(dialog.getByRole('button', { name: '전환' }))
    await waitFor(() => expect(localStorage.getItem('accessToken')).toBe('switched-token'))
    expect(post).toHaveBeenCalledWith('/auth/data-context', { credentialId: 20 })
    expect(getSessionGeneration()).toBeGreaterThan(generation)
  })

  it('keeps token, session and screen when switching fails', async () => {
    const { post } = setup({ switchError: 'DATA_CONTEXT_ACCESS_DENIED' })
    const generation = getSessionGeneration()
    const user = useAuthStore.getState().user
    fireEvent.click(await card().findByRole('button', { name: '꼬마별궁수 계정으로 전환' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '전환' }))
    const alert = await card().findByRole('alert')
    expect(alert.textContent).toContain('선택할 수 없는 계정입니다')
    expect(post).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('accessToken')).toBe('test-token')
    expect(getSessionGeneration()).toBe(generation)
    expect(useAuthStore.getState().user).toBe(user)
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(card().getAllByTestId('data-context-group')).toHaveLength(2)
  })

  it('warns about unsaved character changes only when the page is dirty', async () => {
    setup()
    const button = await card().findByRole('button', { name: '꼬마별궁수 계정으로 전환' })
    fireEvent.click(button)
    expect(
      within(screen.getByRole('dialog')).queryByText(/저장하지 않은 캐릭터 변경사항/)
    ).toBeNull()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '취소' }))

    fireEvent.click(
      await screen.findByRole('button', { name: `${character.characterName} 즐겨찾기 추가` })
    )
    fireEvent.click(button)
    expect(
      within(screen.getByRole('dialog')).getByText(/저장하지 않은 캐릭터 변경사항/)
    ).toBeTruthy()
  })

  it('offers a retry when the account list cannot be loaded', async () => {
    const { get } = setup({ listError: true })
    const alert = await card().findByRole('alert')
    expect(alert.textContent).toContain('연결된 넥슨 계정을 불러오지 못했습니다')
    const before = get.mock.calls.filter(([url]) => url === '/auth/data-contexts').length
    fireEvent.click(within(alert).getByRole('button', { name: '다시 시도' }))
    await waitFor(() =>
      expect(
        get.mock.calls.filter(([url]) => url === '/auth/data-contexts').length
      ).toBeGreaterThan(before)
    )
  })
})

describe('groupDataContexts', () => {
  const rows = contexts.map((row) => {
    const copy = { ...row }
    delete copy.current
    return copy
  })

  it('falls back to the API-key status suffix when the server sends no current flag', () => {
    const groups = groupDataContexts([...rows].reverse(), '77d0')
    expect(groups.map((group) => [group.contextId, group.current])).toEqual([
      [2, true],
      [1, false],
    ])
    expect(groups[0].credentials.map((credential) => credential.current)).toEqual([true])
  })

  it('marks only one key as current when suffixes collide and labels unnamed accounts', () => {
    const unnamed = rows.map((row) => ({
      ...row,
      representativeCharacterName: null,
      keySuffix: 'aaaa',
    }))
    const groups = groupDataContexts(unnamed, 'aaaa')
    expect(
      groups.flatMap((group) => group.credentials).filter((credential) => credential.current)
    ).toHaveLength(1)
    expect(groups.map((group) => group.name)).toEqual(['넥슨 계정 1', '넥슨 계정 2'])
  })
})
