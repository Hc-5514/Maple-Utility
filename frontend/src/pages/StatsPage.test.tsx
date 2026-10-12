// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../api/client'
import StatsPage from './StatsPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPage() {
  const get = vi.spyOn(client, 'get').mockImplementation(async (url) => {
    const path = String(url)
    const params = new URLSearchParams(path.split('?')[1])
    const data = path === '/characters/favorites'
      ? [{ id: 1, characterName: '꼬농' }, { id: 2, characterName: '말랑꼬농' }]
      : path === '/stats/bosses'
        ? ['자쿰', '스우']
        : path.startsWith('/stats/hunting')
          ? {
              totalMeso: 1_320_000_000, totalSolErda: 29, avgDailyMeso: 0, avgDailySolErda: 0,
              dailyRecords: [
                { date: '2026-09-01', mesoEarned: 0, solErdaEarned: 0, playDurationMin: 150 },
                { date: '2026-09-02', mesoEarned: 0, solErdaEarned: 0, playDurationMin: 150 },
              ],
            }
          : path.startsWith('/stats/crystal')
            ? { totalCrystalIncome: 0, weeklyAverage: 0, weeklyRecords: [] }
            : {
                content: [], totalElements: 25,
                totalPages: Math.ceil(25 / Number(params.get('size'))),
                page: Number(params.get('page')), size: Number(params.get('size')),
              }
    return { data: { success: true, data } } as AxiosResponse
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={queryClient}><StatsPage /></QueryClientProvider>)
  return get
}

const calls = (get: { mock: { calls: unknown[][] } }, prefix: string) =>
  get.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith(prefix))
const last = (get: { mock: { calls: unknown[][] } }, prefix: string) => calls(get, prefix).slice(-1)[0]

describe('StatsPage', () => {
  it('renders the hunting cards with totals and daily averages', async () => {
    renderPage()
    expect(await screen.findByText('13.2')).toBeTruthy()
    expect(screen.getByText('6.6')).toBeTruthy()
    expect(screen.getByText('5시간')).toBeTruthy()
    expect(screen.getByText('총 300분')).toBeTruthy()
    expect(screen.getByText('2시간 30분')).toBeTruthy()
  })

  it('applies the single character filter to hunting, crystal and boss items', async () => {
    const get = renderPage()
    fireEvent.click(await screen.findByRole('button', { name: '캐릭터 필터: 전체 즐겨찾기 캐릭터' }))
    fireEvent.click(await screen.findByRole('option', { name: '말랑꼬농' }))

    await waitFor(() => expect(last(get, '/stats/hunting')).toMatch(/^\/stats\/hunting\?characterId=2&/))
    expect(last(get, '/stats/crystal')).toMatch(/^\/stats\/crystal\?characterId=2&/)
    expect(last(get, '/stats/boss-items')).toMatch(/^\/stats\/boss-items\?characterId=2&.*page=0&size=10$/)
  })

  it('sends the boss filter and resets the item page', async () => {
    const get = renderPage()
    const nav = await screen.findByRole('navigation', { name: '보스 아이템 획득 이력 페이지' })
    fireEvent.click(await within(nav).findByRole('button', { name: '3' }))
    await waitFor(() => expect(last(get, '/stats/boss-items')).toMatch(/page=2&size=10$/))

    fireEvent.click(screen.getByRole('button', { name: '보스 필터: 전체 보스' }))
    fireEvent.click(screen.getByRole('option', { name: '스우' }))
    await waitFor(() => expect(last(get, '/stats/boss-items')).toMatch(/bossName=%EC%8A%A4%EC%9A%B0.*page=0&size=10$/))
    expect(calls(get, '/stats/hunting').some((url) => url.includes('bossName'))).toBe(false)
  })

  it('switches the crystal window between four weekly and three four-week bars', async () => {
    const get = renderPage()
    expect(await screen.findByText('최근 4주 총 수익')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '최근 12주' }))
    expect(await screen.findByText('최근 12주 총 수익')).toBeTruthy()
    expect(screen.getByText('4주 평균')).toBeTruthy()
    const [from, to] = last(get, '/stats/crystal').match(/dateFrom=([\d-]+)&dateTo=([\d-]+)/)!.slice(1)
    expect((new Date(to).getTime() - new Date(from).getTime()) / 86_400_000).toBe(83)
  })
})
