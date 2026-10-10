// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../api/client'
import StatsPage from './StatsPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('StatsPage character filters', () => {
  it('filters boss items independently from hunting and crystal stats', async () => {
    const get = vi.spyOn(client, 'get').mockImplementation(async (url) => ({
      data: {
        success: true,
        data: url === '/characters/favorites'
          ? [{ id: 2, characterName: '꼬농' }]
          : null,
      },
    } as AxiosResponse))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={queryClient}><StatsPage /></QueryClientProvider>)

    await screen.findAllByRole('option', { name: '꼬농' })
    fireEvent.change(screen.getByLabelText('보스 아이템 캐릭터'), { target: { value: '2' } })
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringMatching(/^\/stats\/boss-items\?characterId=2&/)))
    expect(get.mock.calls.some(([url]) => String(url).startsWith('/stats/hunting?characterId=2'))).toBe(false)
    expect(get.mock.calls.some(([url]) => String(url).startsWith('/stats/crystal?characterId=2'))).toBe(false)

    fireEvent.change(screen.getByLabelText('사냥·결정석 캐릭터'), { target: { value: '2' } })
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringMatching(/^\/stats\/hunting\?characterId=2&/)))
    expect(get.mock.calls.filter(([url]) => String(url).startsWith('/stats/boss-items?characterId=2'))).toHaveLength(1)
  })
})
