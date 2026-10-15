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
    linkedAt: '2026-09-10T09:00:00',
    representativeCharacterName: '꼬농',
    characterClass: '아크메이지(썬,콜)',
    worldName: '엘리시움',
    characterLevel: 294,
    characterImage: null,
  },
  {
    contextId: 1,
    ownerUserId: 1,
    credentialId: 11,
    keySuffix: '1b3c',
    status: 'EXPIRED',
    current: false,
    lastVerifiedAt: '2026-08-30T21:15:00',
    linkedAt: '2026-09-10T09:00:00',
    representativeCharacterName: '꼬농',
    characterClass: '아크메이지(썬,콜)',
    worldName: '엘리시움',
    characterLevel: 294,
    characterImage: null,
  },
  {
    contextId: 2,
    ownerUserId: 2,
    credentialId: 20,
    keySuffix: '77d0',
    status: 'ACTIVE',
    current: false,
    lastVerifiedAt: '2026-10-02T18:20:00',
    linkedAt: '2026-09-10T09:00:00',
    representativeCharacterName: '꼬마별궁수',
    characterClass: '보우마스터',
    worldName: '엘리시움',
    characterLevel: 260,
    characterImage: null,
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

function setup(
  options: {
    switchError?: string
    listError?: boolean
    rows?: DataContextItem[]
    precheck?: string
    addError?: string
    removeError?: string
  } = {}
) {
  const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
    if (url === '/auth/data-contexts') {
      if (options.listError) throw new Error('network')
      return ok(options.rows ?? contexts)
    }
    if (url === '/characters') return ok([character])
    if (url === '/characters/world-selection') return ok({ worldNames: ['엘리시움'] })
    if (url === '/api-key/status') return ok(status)
    if (url === '/auth/me') return ok({ id: 1, nickname: '테스트', email: null })
    return ok(null)
  })
  const post = vi.spyOn(client, 'post').mockImplementation(async (url) => {
    if (url === '/auth/data-contexts/precheck')
      return ok({ status: options.precheck ?? 'AVAILABLE', connectedCount: 2, maxCount: 5 })
    if (url === '/api-key') {
      if (options.addError) throw failure(options.addError)
      return ok({ registered: true, keyStatus: 'ACTIVE', keySuffix: 'wxyz', lastVerifiedAt: null, auth: switched })
    }
    if (options.switchError) throw failure(options.switchError)
    return ok(switched)
  })
  const del = vi.spyOn(client, 'delete').mockImplementation(async () => {
    if (options.removeError) throw failure(options.removeError)
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
  return { get, post, del }
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
  it('renders three canonical keys in one account when a legacy alias session selects the representative', async () => {
    // The server resolves alias credential 7 to representative 6 and omits the alias row.
    const rows = [6, 8, 9].map((credentialId, index) => ({
      ...contexts[0],
      credentialId,
      keySuffix: ['keyA', 'keyB', 'keyC'][index],
      current: credentialId === 6,
    }))
    setup({ rows })

    await card().findByText('● 현재 사용 중')

    expect(card().getAllByTestId('data-context-group')).toHaveLength(1)
    const keys = card().getAllByTestId('data-context-key')
    expect(keys).toHaveLength(3)
    expect(within(keys[0]).getByText('••••keyA')).toBeTruthy()
    expect(within(keys[0]).getByText('현재 세션')).toBeTruthy()
    expect(card().getByText(/API Key 3\/3개/)).toBeTruthy()
    expect(card().queryByRole('button', { name: /계정으로 전환/ })).toBeNull()
  })

  it('keeps separately verified accounts apart even when their representative names match', async () => {
    setup({ rows: contexts.map((row) => ({ ...row, representativeCharacterName: '동일 닉네임' })) })

    await card().findByText('● 현재 사용 중')

    const groups = card().getAllByTestId('data-context-group')
    expect(groups).toHaveLength(2)
    expect(within(groups[0]).getAllByTestId('data-context-key')).toHaveLength(2)
    expect(within(groups[1]).getAllByTestId('data-context-key')).toHaveLength(1)
    expect(within(groups[1]).getByRole('button', { name: '동일 닉네임 계정으로 전환' })).toBeTruthy()
  })

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

describe('Settings multiple Nexon accounts', () => {
  const fiveAccounts = (): DataContextItem[] =>
    Array.from({ length: 5 }, (_, index) => ({
      ...contexts[0],
      contextId: index + 1,
      credentialId: (index + 1) * 10,
      current: index === 0,
      representativeCharacterName: `계정${index + 1}`,
    }))

  it('shows the account count next to the title with add, unlink and data delete actions', async () => {
    setup()
    await card().findByText('꼬농')
    expect(card().getByText('2개 계정 · 최대 5개')).toBeTruthy()
    expect((card().getByRole('button', { name: '+ 계정 추가' }) as HTMLButtonElement).disabled).toBe(false)
    expect(card().getByRole('button', { name: '꼬농 연결 해제' })).toBeTruthy()
    expect(card().getByRole('button', { name: '꼬마별궁수 데이터 삭제' })).toBeTruthy()
  })

  it('disables adding at the limit and explains why', async () => {
    setup({ rows: fiveAccounts() })
    await card().findByText('계정1')
    expect((card().getByRole('button', { name: '+ 계정 추가' }) as HTMLButtonElement).disabled).toBe(true)
    expect(card().getByText(/최대 5개까지 연결할 수 있습니다/)).toBeTruthy()
  })

  it('prechecks the key before adding the account and closes on success', async () => {
    const { post } = setup()
    await card().findByText('꼬농')
    fireEvent.click(card().getByRole('button', { name: '+ 계정 추가' }))
    const dialog = within(screen.getByRole('dialog'))
    fireEvent.change(dialog.getByLabelText('Nexon API Key'), { target: { value: 'live_new_wxyz' } })
    fireEvent.click(dialog.getByRole('button', { name: '추가' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    const urls = post.mock.calls.map(([url]) => url)
    expect(urls.indexOf('/auth/data-contexts/precheck')).toBeGreaterThanOrEqual(0)
    expect(urls.indexOf('/api-key')).toBeGreaterThan(urls.indexOf('/auth/data-contexts/precheck'))
    expect(post).toHaveBeenCalledWith('/api-key', { apiKey: 'live_new_wxyz' })
  })

  it('stops with a notice and never registers a key linked to another Kakao account', async () => {
    const { post } = setup({ precheck: 'LINKED_TO_OTHER_KAKAO' })
    await card().findByText('꼬농')
    fireEvent.click(card().getByRole('button', { name: '+ 계정 추가' }))
    const dialog = within(screen.getByRole('dialog'))
    fireEvent.change(dialog.getByLabelText('Nexon API Key'), { target: { value: 'live_other' } })
    fireEvent.click(dialog.getByRole('button', { name: '추가' }))
    expect(await dialog.findByRole('alert')).toBeTruthy()
    expect(dialog.getByRole('alert').textContent).toContain('이미 다른 카카오 계정에 연결된 넥슨 계정')
    expect(post.mock.calls.some(([url]) => url === '/api-key')).toBe(false)
    expect((dialog.getByRole('button', { name: '추가' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('shows the failure and offers a retry when adding fails', async () => {
    setup({ addError: 'API_KEY_INVALID' })
    await card().findByText('꼬농')
    fireEvent.click(card().getByRole('button', { name: '+ 계정 추가' }))
    const dialog = within(screen.getByRole('dialog'))
    fireEvent.change(dialog.getByLabelText('Nexon API Key'), { target: { value: 'live_bad' } })
    fireEvent.click(dialog.getByRole('button', { name: '추가' }))
    expect((await dialog.findByRole('alert')).textContent).toContain('API Key가 유효하지 않습니다')
    expect(dialog.getByRole('button', { name: '다시 시도' })).toBeTruthy()
  })

  it('unlinks an account only after confirmation and keeps the dialog on failure', async () => {
    const { del } = setup()
    await card().findByText('꼬농')
    fireEvent.click(card().getByRole('button', { name: '꼬마별궁수 연결 해제' }))
    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getByText(/기록 데이터는 삭제되지 않으며/)).toBeTruthy()
    expect(del).not.toHaveBeenCalled()
    fireEvent.click(dialog.getByRole('button', { name: '연결 해제' }))
    await waitFor(() => expect(del).toHaveBeenCalledWith('/auth/data-contexts/2/link'))
  })

  it('warns that unlinking the current account switches to another account', async () => {
    setup()
    await card().findByText('꼬농')
    fireEvent.click(card().getByRole('button', { name: '꼬농 연결 해제' }))
    expect(within(screen.getByRole('dialog')).getByText(/현재 사용 중인 계정입니다/)).toBeTruthy()
  })

  it('permanently deletes account data only after the irreversible warning is confirmed', async () => {
    const { del } = setup()
    await card().findByText('꼬농')
    fireEvent.click(card().getByRole('button', { name: '꼬마별궁수 데이터 삭제' }))
    const dialog = within(screen.getByRole('dialog'))
    expect(dialog.getByText(/영구 삭제됩니다/)).toBeTruthy()
    expect(dialog.getByText('이 작업은 되돌릴 수 없습니다.')).toBeTruthy()
    expect(del).not.toHaveBeenCalled()
    fireEvent.click(dialog.getByRole('button', { name: '영구 삭제' }))
    await waitFor(() => expect(del).toHaveBeenCalledWith('/auth/data-contexts/2'))
  })

  it('shows a retry when data deletion fails and does not remove anything locally', async () => {
    setup({ removeError: 'SOMETHING' })
    await card().findByText('꼬농')
    fireEvent.click(card().getByRole('button', { name: '꼬마별궁수 데이터 삭제' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '영구 삭제' }))
    const alert = await within(screen.getByRole('dialog')).findByText('데이터 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.')
    expect(alert).toBeTruthy()
    expect(card().getByText('꼬마별궁수')).toBeTruthy()
  })
})

describe('groupDataContexts', () => {
  it('puts the current account first and orders keys by credential id', () => {
    const groups = groupDataContexts(
      [...contexts].reverse().map((row) => ({ ...row, current: row.credentialId === 20 }))
    )
    expect(groups.map((group) => [group.contextId, group.current])).toEqual([
      [2, true],
      [1, false],
    ])
    expect(groups[1].credentials.map((credential) => credential.credentialId)).toEqual([10, 11])
    expect(groups[0].credentials.map((credential) => credential.current)).toEqual([true])
  })

  it('labels accounts without a representative character by order and keeps a null key suffix', () => {
    const unnamed = contexts.map((row) => ({
      ...row,
      representativeCharacterName: null,
      characterClass: null,
      worldName: null,
      characterLevel: null,
      characterImage: null,
      keySuffix: row.credentialId === 20 ? null : row.keySuffix,
    }))
    const groups = groupDataContexts(unnamed)
    expect(groups.map((group) => group.name)).toEqual(['넥슨 계정 1', '넥슨 계정 2'])
    expect(groups.map((group) => group.detail)).toEqual(['', ''])
    expect(groups[1].credentials[0].keySuffix).toBeNull()
  })
})
