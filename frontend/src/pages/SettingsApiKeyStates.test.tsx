// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import client from '../api/client'
import { useAuthStore } from '../stores/authStore'
import { useCharacterStore } from '../stores/characterStore'
import { useThemeStore } from '../stores/themeStore'
import type { ApiKeyStatusResponse, ApiResponse } from '../types'
import { getSessionGeneration } from '../api/session'
import SettingsPage, { formatVerifiedAt } from './SettingsPage'

const activeStatus: ApiKeyStatusResponse = {
  registered: true,
  keyStatus: 'ACTIVE',
  keySuffix: 'abcd',
  lastVerifiedAt: '2026-09-17T00:00:00',
}
const unregisteredStatus: ApiKeyStatusResponse = {
  registered: false,
  keyStatus: null,
  keySuffix: null,
  lastVerifiedAt: null,
}
const initialAuth = useAuthStore.getState()
const initialCharacters = useCharacterStore.getState()
const initialTheme = useThemeStore.getState()
const queryClients: QueryClient[] = []

function response<T>(data: T) {
  return { data: { success: true, data } } as AxiosResponse<ApiResponse<T>>
}

function setup(statusProvider: () => Promise<ApiKeyStatusResponse>) {
  const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
    if (url === '/api-key/status') return response(await statusProvider())
    if (url === '/characters') return response([])
    if (url === '/characters/world-selection') return response({ worldNames: [] })
    if (url === '/auth/data-contexts') return response([])
    throw new Error(`Unexpected test GET: ${url}`)
  })
  const post = vi.spyOn(client, 'post').mockResolvedValue(response(null))
  const remove = vi.spyOn(client, 'delete').mockResolvedValue(response(null))
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  queryClients.push(queryClient)
  render(
    <QueryClientProvider client={queryClient}>
      <SettingsPage />
    </QueryClientProvider>
  )
  return { get, post, remove, queryClient, card: within(screen.getByTestId('api-key-card')) }
}

beforeEach(() => {
  const items = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => items.get(key) ?? null,
    setItem: (key: string, value: string) => items.set(key, value),
    removeItem: (key: string) => items.delete(key),
  })
  useAuthStore.setState({ hasApiKey: false, isInitializing: false })
  useCharacterStore.setState({ characters: [] })
  useThemeStore.setState({ theme: 'light' })
})

afterEach(() => {
  cleanup()
  queryClients.splice(0).forEach((queryClient) => queryClient.clear())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  useAuthStore.setState(initialAuth)
  useCharacterStore.setState(initialCharacters)
  useThemeStore.setState(initialTheme)
})

describe('Settings API Key states', () => {
  it.each([
    ['DATA_CONTEXT_CONFLICT', '이 넥슨 계정은 다른 카카오 계정에 연결되어 있습니다.'],
    ['NEXON_ACCOUNT_UNIDENTIFIABLE', '넥슨 계정을 확인할 수 없습니다. API Key와 계정의 캐릭터 정보를 확인해 주세요.'],
  ])('preserves the current session and caches when registration fails with %s', async (code, message) => {
    const { card, post, queryClient } = setup(async () => unregisteredStatus)
    queryClient.setQueryData(['stats/hunting'], { totalMeso: 500 })
    const oldUser = useAuthStore.getState().user
    const generation = getSessionGeneration()
    localStorage.setItem('accessToken', 'previous-token')
    post.mockRejectedValueOnce({ isAxiosError: true, response: { data: { code } } })
    fireEvent.change(await card.findByLabelText('Nexon API Key'), { target: { value: 'failed-key-abcd' } })

    fireEvent.click(card.getByRole('button', { name: '등록' }))

    expect(await card.findByText(message)).toBeTruthy()
    expect(getSessionGeneration()).toBe(generation)
    expect(localStorage.getItem('accessToken')).toBe('previous-token')
    expect(useAuthStore.getState().user).toBe(oldUser)
    expect(queryClient.getQueryData(['stats/hunting'])).toEqual({ totalMeso: 500 })
  })

  it('registers an unregistered key and replaces the form with its masked status', async () => {
    let status = unregisteredStatus
    const { card, post } = setup(async () => status)
    const input = await card.findByLabelText('Nexon API Key', { selector: 'input' })
    const submit = card.getByRole('button', { name: '등록' }) as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    expect(card.queryByRole('button', { name: '삭제' })).toBeNull()
    fireEvent.change(input, { target: { value: '   ' } })
    expect(submit.disabled).toBe(true)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(input, { target: { value: '  secret-key-abcd  ' } })
    status = activeStatus

    fireEvent.click(submit)

    await screen.findByText('● ACTIVE')
    expect(post).toHaveBeenCalledExactlyOnceWith('/api-key', { apiKey: 'secret-key-abcd' })
    expect(screen.queryByLabelText('Nexon API Key', { selector: 'input' })).toBeNull()
    expect(screen.getByText('•••••••• – •••••••• – •••••••• – ••••abcd')).toBeTruthy()
    expect(screen.queryByText('secret-key-abcd')).toBeNull()
    expect(useAuthStore.getState().hasApiKey).toBe(true)
  })

  it.each(['EXPIRED', 'INVALID'] as const)(
    'offers replacement registration for a registered %s key',
    async (keyStatus) => {
      const { card, post } = setup(async () => ({ ...activeStatus, keyStatus }))

      await card.findByText(`● ${keyStatus}`)

      expect(card.getByText('API Key가 유효하지 않습니다. 새로운 키를 등록해 주세요.')).toBeTruthy()
      expect(card.getByLabelText('Nexon API Key', { selector: 'input' })).toBeTruthy()
      expect(card.getByRole('button', { name: '삭제' })).toBeTruthy()
      expect(post).not.toHaveBeenCalled()
    }
  )

  it('keeps registration hidden until the pending status request completes', async () => {
    let resolveStatus!: (status: ApiKeyStatusResponse) => void
    const pendingStatus = new Promise<ApiKeyStatusResponse>((resolve) => {
      resolveStatus = resolve
    })
    const { card } = setup(() => pendingStatus)

    expect(card.getByRole('status').textContent).toContain('API Key 상태를 불러오는 중...')
    expect(card.queryByLabelText('Nexon API Key', { selector: 'input' })).toBeNull()
    expect(card.queryByText('넥슨 게임 데이터를 불러오려면 API Key를 등록해 주세요.')).toBeNull()
    await act(async () => resolveStatus(activeStatus))

    await card.findByText('● ACTIVE')
    expect(card.queryByRole('status')).toBeNull()
    expect(card.queryByLabelText('Nexon API Key', { selector: 'input' })).toBeNull()
  })

  it('recovers from a status fetch error only after the user retries', async () => {
    const statusProvider = vi.fn<() => Promise<ApiKeyStatusResponse>>()
      .mockRejectedValueOnce(new Error('status unavailable'))
      .mockResolvedValue(activeStatus)
    const { card } = setup(statusProvider)
    await card.findByText('API Key 상태를 불러오지 못했습니다.')
    expect(card.queryByLabelText('Nexon API Key', { selector: 'input' })).toBeNull()
    expect(statusProvider).toHaveBeenCalledTimes(1)

    fireEvent.click(card.getByRole('button', { name: '다시 시도' }))

    await card.findByText('● ACTIVE')
    expect(card.queryByRole('alert')).toBeNull()
    expect(statusProvider).toHaveBeenCalledTimes(2)
  })

  it('retains the entered key after registration fails and allows a successful retry', async () => {
    let status = unregisteredStatus
    const { card, post } = setup(async () => status)
    post.mockRejectedValueOnce(new Error('registration unavailable'))
    const input = (await card.findByLabelText('Nexon API Key', {
      selector: 'input',
    })) as HTMLInputElement
    fireEvent.change(input, { target: { value: 'retry-key-abcd' } })

    fireEvent.click(card.getByRole('button', { name: '등록' }))

    await card.findByRole('alert')
    expect(input.value).toBe('retry-key-abcd')
    expect(input.type).toBe('password')
    expect(useAuthStore.getState().hasApiKey).toBe(false)
    status = activeStatus
    fireEvent.click(card.getByRole('button', { name: '등록' }))

    await screen.findByText('● ACTIVE')
    expect(post).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('retains API access after deletion fails and clears it only after a successful retry', async () => {
    let status = activeStatus
    useAuthStore.setState({ hasApiKey: true })
    const { card, remove } = setup(async () => status)
    remove.mockRejectedValueOnce(new Error('delete unavailable'))
    fireEvent.click(await card.findByRole('button', { name: '삭제' }))
    const dialog = within(screen.getByRole('dialog', { name: 'API Key 삭제' }))

    fireEvent.click(dialog.getByRole('button', { name: '삭제' }))

    await dialog.findByText('삭제 중 오류가 발생했습니다. 다시 시도해 주세요.')
    expect(useAuthStore.getState().hasApiKey).toBe(true)
    expect(card.getByText('● ACTIVE')).toBeTruthy()
    status = unregisteredStatus
    fireEvent.click(dialog.getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await card.findByLabelText('Nexon API Key', { selector: 'input' })
    expect(remove).toHaveBeenCalledTimes(2)
    expect(remove).toHaveBeenLastCalledWith('/api-key')
    expect(useAuthStore.getState().hasApiKey).toBe(false)
    expect(card.queryByText('● ACTIVE')).toBeNull()
  })

  it('does not send a deletion request when the user cancels confirmation', async () => {
    const { card, remove } = setup(async () => activeStatus)
    fireEvent.click(await card.findByRole('button', { name: '삭제' }))

    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '취소' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(remove).not.toHaveBeenCalled()
    expect(card.getByText('● ACTIVE')).toBeTruthy()
  })
})

describe('Settings authentication time in Asia/Seoul', () => {
  it.each([
    ['2026-09-16T14:59:59Z', '2026.09.16 23:59'],
    ['2026-09-16T15:00:00Z', '2026.09.17 00:00'],
    ['2026-09-17T00:00:00', '2026.09.17 00:00'],
    ['2026-09-17T00:00:00+09:00', '2026.09.17 00:00'],
  ])('formats %s at the correct Seoul date boundary', (input, expected) => {
    expect(formatVerifiedAt(input)).toBe(expected)
  })

  it.each([null, '', 'invalid-timestamp'])('shows an unauthenticated label for %s', (input) => {
    expect(formatVerifiedAt(input)).toBe('미인증')
  })
})
