// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, Link, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosResponse } from 'axios'
import client from '../api/client'
import SettingsPage, { formatVerifiedAt, moveVisibleNeighbor } from './SettingsPage'
import Layout from '../components/layout/Layout'
import { useAuthStore } from '../stores/authStore'
import { settingsFigmaCharacters } from '../mocks/fixtures/settingsFigma'
import type { ApiKeyStatusResponse } from '../types'

const original = settingsFigmaCharacters.map((c, index) => ({ ...c, sortOrder: index + 1 }))
const NativeRequest = Request
beforeEach(() => {
  vi.stubGlobal(
    'Request',
    class extends NativeRequest {
      constructor(input: RequestInfo | URL, init?: RequestInit) {
        super(input, { ...init, signal: undefined })
      }
    }
  )
  const storage = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
    clear: () => storage.clear(),
  })
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
function setup(
  options: {
    conflict?: boolean
    failSave?: boolean
    worlds?: string[]
    status?: ApiKeyStatusResponse
  } = {}
) {
  let saved = original.map((c) => ({ ...c }))
  let selected = options.worlds ?? ['엘리시움']
  const get = vi.spyOn(client, 'get').mockImplementation(
    async (url) =>
      ({
        data: {
          success: true,
          data:
            url === '/characters'
              ? saved
              : url === '/characters/world-selection'
                ? { worldNames: selected }
                : (options.status ?? {
                    registered: true,
                    keySuffix: '9f2a',
                    keyStatus: 'ACTIVE',
                    lastVerifiedAt: '2026-09-17T09:42:00',
                  }),
        },
      }) as AxiosResponse
  )
  const put = vi.spyOn(client, 'put').mockImplementation(async (url, body) => {
    if (url === '/characters/preferences') {
      if (options.conflict)
        throw new AxiosError('캐릭터 목록 변경', undefined, undefined, undefined, {
          status: 409,
          data: {},
          headers: {},
          statusText: 'Conflict',
          config: {},
        } as AxiosResponse)
      if (options.failSave) throw new Error('save failed')
      const preferences = (body as { characters: { characterId: number; favorite: boolean }[] })
        .characters
      saved = preferences.map((c, index) => ({
        ...saved.find((s) => s.id === c.characterId)!,
        favorite: c.favorite,
        sortOrder: index + 1,
      }))
      return { data: { success: true, data: saved } } as AxiosResponse
    }
    selected = (body as { worldNames: string[] }).worldNames
    return { data: { success: true, data: { worldNames: selected } } } as AxiosResponse
  })
  useAuthStore.setState({
    user: { id: 1, nickname: '테스트', email: null, oauthProvider: 'KAKAO' },
    hasApiKey: true,
    isInitializing: false,
  })
  localStorage.setItem('accessToken', 'test-token')
  const router = createMemoryRouter(
    [
      {
        element: <Layout />,
        children: [
          { path: '/settings', element: <SettingsPage /> },
          {
            path: '/dashboard',
            element: (
              <div>
                <p>대시보드 도착</p>
                <Link to="/settings">설정 복귀</Link>
              </div>
            ),
          },
        ],
      },
      { path: '/login', element: <p>로그인 도착</p> },
    ],
    { initialEntries: ['/settings'] }
  )
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
  return { put, get, router }
}
const rows = () =>
  screen.getAllByTestId('character-row').map((row) => row.getAttribute('data-character-id'))
describe('Settings preferences', () => {
  it('stages favorite and all-character order changes, saves once and clears dirty state', async () => {
    const { put } = setup()
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '꼬마렌의일기 즐겨찾기 추가' }))
    fireEvent.click(screen.getByRole('button', { name: '꼬마렌의일기 위로 이동' }))
    expect(rows()).toEqual(['1', '3', '2', '4', '5', '6'])
    expect(put).not.toHaveBeenCalled()
    expect(screen.getByText('저장하지 않은 변경사항이 있어요')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '저장' }))
    await waitFor(() => expect(screen.queryByText('저장하지 않은 변경사항이 있어요')).toBeNull())
    expect(put).toHaveBeenCalledWith('/characters/preferences', {
      characters: [
        { characterId: 1, favorite: true },
        { characterId: 3, favorite: true },
        { characterId: 2, favorite: true },
        { characterId: 4, favorite: false },
        { characterId: 5, favorite: false },
        { characterId: 6, favorite: false },
      ],
    })
  })
  it('clears dirty indication when the user reverses a change', async () => {
    setup()
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '도끼꼬농 즐겨찾기 추가' }))
    fireEvent.click(screen.getByRole('button', { name: '도끼꼬농 즐겨찾기 해제' }))
    expect(screen.queryByText('저장하지 않은 변경사항이 있어요')).toBeNull()
    expect((screen.getByRole('button', { name: '저장' }) as HTMLButtonElement).disabled).toBe(true)
  })
  it('retains changes after failed save and allows retry', async () => {
    const { put } = setup({ failSave: true })
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '도끼꼬농 위로 이동' }))
    fireEvent.click(screen.getByRole('button', { name: '저장' }))
    await screen.findByRole('alert')
    expect(rows()).toEqual(['1', '2', '3', '4', '6', '5'])
    expect(screen.getByText('저장하지 않은 변경사항이 있어요')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '저장' }))
    await waitFor(() => expect(put).toHaveBeenCalledTimes(2))
  })
  it('requires confirmation to discard a stale draft after a list conflict', async () => {
    setup({ conflict: true })
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '도끼꼬농 위로 이동' }))
    fireEvent.click(screen.getByRole('button', { name: '저장' }))
    fireEvent.click(await screen.findByRole('button', { name: '최신 목록 불러오기' }))
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(rows()).toEqual(['1', '2', '3', '4', '6', '5'])
    fireEvent.click(screen.getByRole('button', { name: '최신 목록 불러오기' }))
    fireEvent.click(screen.getByRole('button', { name: '변경사항 버리고 새로고침' }))
    await waitFor(() => expect(rows()).toEqual(['1', '2', '3', '4', '5', '6']))
    expect(screen.queryByText('저장하지 않은 변경사항이 있어요')).toBeNull()
  })
  it('refreshes characters after key registration while retaining the current draft', async () => {
    const { get } = setup({
      status: { registered: false, keyStatus: null, keySuffix: null, lastVerifiedAt: null },
    })
    const post = vi
      .spyOn(client, 'post')
      .mockResolvedValue({ data: { success: true, data: {} } } as AxiosResponse)
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '도끼꼬농 위로 이동' }))
    const before = get.mock.calls.filter(([url]) => url === '/characters').length
    fireEvent.change(screen.getByLabelText('Nexon API Key', { selector: 'input' }), {
      target: { value: 'test-key-abcd' },
    })
    fireEvent.click(screen.getByRole('button', { name: '등록' }))
    await waitFor(() =>
      expect(get.mock.calls.filter(([url]) => url === '/characters').length).toBeGreaterThan(before)
    )
    expect(post).toHaveBeenCalledWith('/api-key', { apiKey: 'test-key-abcd' })
    expect(rows()).toEqual(['1', '2', '3', '4', '6', '5'])
    expect(screen.getByText('저장하지 않은 변경사항이 있어요')).toBeTruthy()
  })
  it('keeps the refresh button available and confirms draft disposal before synchronizing', async () => {
    setup()
    const post = vi.spyOn(client, 'post').mockResolvedValue({
      data: { success: true, data: { id: 99, status: 'COMPLETED' } },
    } as AxiosResponse)
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '도끼꼬농 위로 이동' }))
    const refresh = screen.getByRole('button', { name: '캐릭터 새로고침' })
    expect((refresh as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(refresh)
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(post).not.toHaveBeenCalled()
    expect(rows()).toEqual(['1', '2', '3', '4', '6', '5'])
    fireEvent.click(refresh)
    fireEvent.click(screen.getByRole('button', { name: '변경사항 버리고 새로고침' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/characters/sync'))
    expect(rows()).toEqual(['1', '2', '3', '4', '5', '6'])
    expect(screen.queryByText('저장하지 않은 변경사항이 있어요')).toBeNull()
  })
  it('opens the reusable world modal, cancels with Escape, and saves zero selection', async () => {
    const { put } = setup()
    await screen.findByText('꼬농')
    const opener = screen.getByRole('button', { name: '월드 선택' })
    fireEvent.click(opener)
    expect(await screen.findByRole('dialog')).toBeTruthy()
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(put).not.toHaveBeenCalled()
    fireEvent.click(opener)
    fireEvent.click(screen.getByRole('button', { name: '초기화' }))
    fireEvent.click(screen.getByRole('button', { name: '선택 완료' }))
    await screen.findByText('월드를 선택하면 해당 월드의 캐릭터가 표시됩니다.')
    expect(put).toHaveBeenCalledWith('/characters/world-selection', { worldNames: [] })
  })
  it('blocks page navigation with unsaved changes and cancels or confirms', async () => {
    const { router } = setup()
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '꼬농 즐겨찾기 해제' }))
    fireEvent.click(screen.getByRole('link', { name: '대시보드' }))
    const dialog = await screen.findByRole('dialog')
    expect(router.state.location.pathname).toBe('/settings')
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    fireEvent.click(screen.getByRole('link', { name: '대시보드' }))
    fireEvent.click(await screen.findByRole('button', { name: '변경사항 버리고 이동' }))
    await screen.findByText('대시보드 도착')
    fireEvent.click(screen.getByRole('link', { name: '설정 복귀' }))
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '꼬농 즐겨찾기 해제' }))
    fireEvent.click(screen.getByRole('link', { name: '대시보드' }))
    expect(await screen.findByRole('dialog')).toBeTruthy()
    expect(router.state.location.pathname).toBe('/settings')
  })
  it('keeps authentication until logout confirmation', async () => {
    setup()
    await screen.findByText('꼬농')
    fireEvent.click(screen.getByRole('button', { name: '꼬농 즐겨찾기 해제' }))
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    await screen.findByRole('dialog')
    expect(localStorage.getItem('accessToken')).toBe('test-token')
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(useAuthStore.getState().user).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    fireEvent.click(await screen.findByRole('button', { name: '변경사항 버리고 이동' }))
    await screen.findByText('로그인 도착')
    expect(localStorage.getItem('accessToken')).toBeNull()
  })
  it('shows a replacement form for registered INVALID keys and only the suffix', async () => {
    setup({
      status: {
        registered: true,
        keyStatus: 'INVALID',
        keySuffix: 'abcd',
        lastVerifiedAt: '2026-09-17T00:42:00Z',
      },
    })
    expect(await screen.findByText('● INVALID')).toBeTruthy()
    expect(screen.getByText('•••••••• – •••••••• – •••••••• – ••••abcd')).toBeTruthy()
    expect(screen.getByText('2026.09.17 09:42')).toBeTruthy()
    expect(screen.getByLabelText('Nexon API Key', { selector: 'input' })).toBeTruthy()
  })
  it('preserves hidden positions when swapping visible neighbors', () => {
    const next = moveVisibleNeighbor(original, [1, 3, 6], 6, -1)
    expect(next.map((c) => c.id)).toEqual([1, 2, 6, 4, 5, 3])
    expect(formatVerifiedAt('2026-09-17T09:42:00')).toBe('2026.09.17 09:42')
  })
})
