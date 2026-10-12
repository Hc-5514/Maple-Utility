// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import type { BossPeriod } from '../../types'
import BossContent from './BossContent'

const period: BossPeriod = {
  bossId: 5, characterId: 1, periodStart: '2026-10-08', partySize: 1,
  crystalPrice: 48_900_000, savedAt: null,
  items: [{
    dropItem: {
      id: 10, bossId: 5, itemName: '강렬한 힘의 결정', itemImage: null,
      itemDescription: null, dropRateTier: 'HIGH', itemKind: 'CRYSTAL', defaultQuantity: 1,
    },
    acquired: false, quantity: 1, mesoAmount: 48_900_000,
  }],
}

async function openSelection() {
  const button = await screen.findByRole('button', { name: '보스 선택' }) as HTMLButtonElement
  await waitFor(() => expect(button.disabled).toBe(false))
  fireEvent.click(button)
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('BossContent', () => {
  it('keeps weekly and monthly period selections independent', async () => {
    const get = vi.spyOn(client, 'get').mockResolvedValue({
      data: { success: true, data: { weeklyBosses: [], monthlyBosses: [] } },
    } as AxiosResponse)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><BossContent characterId={1} date="2026-10-08" /></QueryClientProvider>)

    await waitFor(() => expect(get).toHaveBeenCalledWith(
      '/scheduler/1/boss?weeklyDate=2026-10-08&monthlyDate=2026-10-01',
    ))
    fireEvent.click(screen.getByRole('button', { name: /^보스 월/ }))
    fireEvent.click(screen.getByRole('option', { name: '2026년 9월' }))

    await waitFor(() => expect(get).toHaveBeenLastCalledWith(
      '/scheduler/1/boss?weeklyDate=2026-10-08&monthlyDate=2026-09-01',
    ))
  })

  it('writes one period snapshot only after the section save command', async () => {
    vi.spyOn(client, 'get').mockImplementation(async (url) => ({
      data: { success: true, data: url.startsWith('/scheduler/') ? {
        weeklyBosses: [{
          characterId: 1, bossId: 5, bossName: '스우', difficulty: 'HARD',
          resetPeriod: 'WEEKLY', isCompleted: true, syncedAt: null,
        }], monthlyBosses: [],
      } : period },
    } as AxiosResponse))
    const put = vi.spyOn(client, 'put').mockResolvedValue({
      data: { success: true, data: { ...period, savedAt: '2026-10-08T12:00:00' } },
    } as AxiosResponse)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><BossContent characterId={1} date="2026-10-08" /></QueryClientProvider>)

    fireEvent.click((await screen.findAllByRole('button', { name: /스우/ }))[0])
    fireEvent.click(await screen.findByRole('checkbox', { name: '강렬한 힘의 결정 획득 여부' }))
    expect(put).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '저장' }))
    fireEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(put).toHaveBeenCalledWith('/boss/5/period', expect.objectContaining({
      characterId: 1,
      periodStart: '2026-10-08',
      items: [expect.objectContaining({ bossDropItemId: 10, acquired: true })],
    })))
  })

  it('restores the modal edits on cancel before the section save', async () => {
    vi.spyOn(client, 'get').mockImplementation(async (url) => ({
      data: { success: true, data: url.startsWith('/scheduler/') ? {
        weeklyBosses: [{
          characterId: 1, bossId: 5, bossName: '스우', difficulty: 'HARD',
          resetPeriod: 'WEEKLY', isCompleted: true, syncedAt: null,
        }], monthlyBosses: [],
      } : period },
    } as AxiosResponse))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><BossContent characterId={1} date="2026-10-08" /></QueryClientProvider>)

    fireEvent.click((await screen.findAllByRole('button', { name: /스우/ }))[0])
    const checkbox = await screen.findByRole('checkbox', { name: '강렬한 힘의 결정 획득 여부' })
    fireEvent.click(checkbox)
    expect((checkbox as HTMLInputElement).checked).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    fireEvent.click((await screen.findAllByRole('button', { name: /스우/ }))[0])
    expect((await screen.findByRole('checkbox', { name: '강렬한 힘의 결정 획득 여부' }) as HTMLInputElement).checked).toBe(false)
  })

  it('saves a manually selected weekly boss when the selected week has no record', async () => {
    vi.spyOn(client, 'get').mockImplementation(async (url) => {
      if (url.includes('/candidates')) {
        return { data: { success: true, data: url.includes('WEEKLY') ? [{
          id: 5, bossName: '스우', difficulty: 'HARD', bossImage: null,
          crystalPrice: 51_500_000, resetPeriod: 'WEEKLY',
        }] : [] } } as AxiosResponse
      }
      return { data: { success: true, data: { weeklyBosses: [], monthlyBosses: [], weeklyCustomizable: true, monthlyCustomizable: true } } } as AxiosResponse
    })
    const post = vi.spyOn(client, 'post').mockResolvedValue({
      data: { success: true, data: [] },
    } as AxiosResponse)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><BossContent characterId={1} date="2026-10-08" /></QueryClientProvider>)

    await openSelection()
    fireEvent.click(await screen.findByRole('checkbox', { name: '스우 HARD' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '저장' }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('/scheduler/1/boss/selection', {
      periodStart: '2026-10-08', resetPeriod: 'WEEKLY', bossIds: [5],
    }))
  })

  it('limits one difficulty per boss and removes an unchecked boss after confirmation', async () => {
    vi.spyOn(client, 'get').mockImplementation(async (url) => {
      if (url.includes('/candidates')) {
        return { data: { success: true, data: url.includes('WEEKLY') ? [
          { id: 5, bossName: '스우', difficulty: 'HARD', bossImage: null, crystalPrice: 1, resetPeriod: 'WEEKLY' },
          { id: 6, bossName: '스우', difficulty: 'EXTREME', bossImage: null, crystalPrice: 1, resetPeriod: 'WEEKLY' },
        ] : [] } } as AxiosResponse
      }
      return { data: { success: true, data: { weeklyBosses: [{
        characterId: 1, bossId: 5, bossName: '스우', difficulty: 'HARD',
        resetPeriod: 'WEEKLY', isCompleted: true, syncedAt: null,
      }], monthlyBosses: [], weeklyCustomizable: true, monthlyCustomizable: true } } } as AxiosResponse
    })
    const remove = vi.spyOn(client, 'delete').mockResolvedValue({ data: { success: true } } as AxiosResponse)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><BossContent characterId={1} date="2026-10-08" /></QueryClientProvider>)

    await openSelection()
    const hard = await screen.findByRole('checkbox', { name: '스우 HARD' }) as HTMLInputElement
    const extreme = screen.getByRole('checkbox', { name: '스우 EXTREME' }) as HTMLInputElement
    expect(hard.checked).toBe(true)
    expect(extreme.disabled).toBe(true)
    fireEvent.click(hard)
    expect(extreme.disabled).toBe(false)
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '저장' }))
    fireEvent.click(await screen.findByRole('button', { name: '삭제 후 저장' }))

    await waitFor(() => expect(remove).toHaveBeenCalledWith(
      '/scheduler/1/boss/selection/5?periodStart=2026-10-08&resetPeriod=WEEKLY',
    ))
  })

  it('locks boss customization for recent scheduler periods', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({
      data: { success: true, data: { weeklyBosses: [{
        characterId: 1, bossId: 5, bossName: '스우', difficulty: 'HARD',
        resetPeriod: 'WEEKLY', isCompleted: true, syncedAt: null,
      }], monthlyBosses: [], weeklyCustomizable: false, monthlyCustomizable: false } },
    } as AxiosResponse)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><BossContent characterId={1} date="2026-10-08" /></QueryClientProvider>)

    expect(await screen.findByText('스우')).toBeTruthy()
    expect((screen.getByRole('button', { name: '보스 선택' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.queryByRole('button', { name: '스우 삭제' })).toBeNull()
  })

  it('deletes a boss and its selected-period acquisitions after confirmation', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({
      data: { success: true, data: { weeklyBosses: [{
        characterId: 1, bossId: 5, bossName: '스우', difficulty: 'HARD',
        resetPeriod: 'WEEKLY', isCompleted: true, syncedAt: null,
      }], monthlyBosses: [], weeklyCustomizable: true, monthlyCustomizable: true } },
    } as AxiosResponse)
    const remove = vi.spyOn(client, 'delete').mockResolvedValue({ data: { success: true } } as AxiosResponse)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><BossContent characterId={1} date="2026-10-08" /></QueryClientProvider>)
    fireEvent.click(await screen.findByRole('button', { name: '스우 삭제' }))
    fireEvent.click(await screen.findByRole('button', { name: '삭제' }))

    await waitFor(() => expect(remove).toHaveBeenCalledWith(
      '/scheduler/1/boss/selection/5?periodStart=2026-10-08&resetPeriod=WEEKLY',
    ))
  })
})
