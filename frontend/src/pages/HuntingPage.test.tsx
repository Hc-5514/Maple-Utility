// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../api/client'
import HuntingPage from './HuntingPage'
import type { HuntingRecord } from '../types'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const record = (id: number, recordDate: string): HuntingRecord => ({
  id,
  characterId: 1,
  recordDate,
  mesoEarned: 230_000_000,
  solErdaEarned: 16,
  playDurationMin: 150,
  huntingGroundId: 1,
  regionName: '고통의 미궁',
  huntingGround: '고통의 미궁 중심부 6',
  memo: '월말 집중 파밍',
  createdAt: '',
  updatedAt: '',
})

function renderPage() {
  const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
    const path = String(url)
    const params = new URLSearchParams(path.split('?')[1])
    const data = path === '/characters/favorites'
      ? [{ id: 1, characterName: '꼬농' }, { id: 2, characterName: '말랑꼬농' }]
      : path.startsWith('/hunting/summary')
        ? { recordCount: 25, totalMeso: 4_325_000_000, totalSolErda: 217, totalPlayDurationMin: 2250 }
        : {
            content: [record(Number(params.get('page')) + 1, '2026-09-30')],
            totalElements: 25,
            totalPages: Math.ceil(25 / Number(params.get('size'))),
            page: Number(params.get('page')),
            size: Number(params.get('size')),
          }
    return { data: { success: true, data } } as AxiosResponse
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <HuntingPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return get
}

const pageCalls = (get: { mock: { calls: unknown[][] } }) =>
  get.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/hunting/page'))
const lastPageCall = (get: { mock: { calls: unknown[][] } }) => pageCalls(get).slice(-1)[0]

describe('HuntingPage', () => {
  it('renders summary cards, totals and the first page with 10 rows per page', async () => {
    const get = renderPage()

    expect(await screen.findByText('43억 2500만')).toBeTruthy()
    expect(screen.getByText('25개 기록')).toBeTruthy()
    expect(screen.getAllByText('4,325,000,000')).toHaveLength(2)
    expect(await screen.findByText('2시간 30분')).toBeTruthy()
    expect(pageCalls(get)[0]).toMatch(/page=0&size=10$/)
    expect(screen.getByRole('button', { name: '표시 건수: 10개씩 보기' })).toBeTruthy()
  })

  it('resets to the first page when the page size changes', async () => {
    const get = renderPage()
    const pagination = await screen.findByRole('navigation', { name: '사냥 기록 페이지' })

    fireEvent.click(await within(pagination).findByRole('button', { name: '3' }))
    await waitFor(() => expect(lastPageCall(get)).toMatch(/page=2&size=10$/))

    fireEvent.click(screen.getByRole('button', { name: '표시 건수: 10개씩 보기' }))
    fireEvent.click(screen.getByRole('option', { name: '20개씩' }))
    await waitFor(() => expect(lastPageCall(get)).toMatch(/page=0&size=20$/))
    expect(screen.getByRole('button', { name: '표시 건수: 20개씩 보기' })).toBeTruthy()
  })

  it('filters by favorite character and resets the page', async () => {
    const get = renderPage()
    const pagination = await screen.findByRole('navigation', { name: '사냥 기록 페이지' })
    fireEvent.click(await within(pagination).findByRole('button', { name: '2' }))
    await waitFor(() => expect(lastPageCall(get)).toMatch(/page=1&size=10$/))

    fireEvent.click(screen.getByRole('button', { name: '캐릭터 필터: 전체 즐겨찾기 캐릭터' }))
    fireEvent.click(await screen.findByRole('option', { name: '말랑꼬농' }))
    await waitFor(() => expect(lastPageCall(get)).toMatch(/^\/hunting\/page\?characterId=2&.*page=0&size=10$/))
    expect(get.mock.calls.some(([url]) => String(url).startsWith('/hunting/summary?characterId=2'))).toBe(true)
  })

  it('asks for confirmation before deleting a record', async () => {
    renderPage()
    const remove = vi.spyOn(client, 'delete').mockResolvedValue({ data: null } as AxiosResponse)

    fireEvent.click(await screen.findByRole('button', { name: '2026-09-30 기록 삭제' }))
    const dialog = screen.getByRole('alertdialog', { name: '사냥 기록 삭제' })
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }))
    await waitFor(() => expect(remove).toHaveBeenCalledWith('/hunting/1'))
  })
})
