// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import type { BossDropItemAcquisitionStatus, SchedulerBossRecord } from '../../types'
import BossDropModal from './BossDropModal'

const record: SchedulerBossRecord = {
  characterId: 1,
  bossId: 5,
  bossName: '데미안',
  difficulty: 'HARD',
  resetPeriod: 'WEEKLY',
  isCompleted: true,
  syncedAt: '2026-10-07T01:00:00',
}

const dropItem = {
  id: 10,
  bossId: 5,
  itemName: '마력이 깃든 장비',
  itemImage: null,
  itemDescription: null,
  dropRateTier: 'NORMAL' as const,
}

function renderModal() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <BossDropModal isOpen onClose={vi.fn()} record={record} characterId={1} />
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('BossDropModal', () => {
  it('loads the boss-specific status response and shows its drop item', async () => {
    const statuses: BossDropItemAcquisitionStatus[] = [
      { dropItem, acquired: false, acquisitions: [] },
    ]
    const get = vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: statuses } } as AxiosResponse)

    renderModal()

    expect(await screen.findByText(dropItem.itemName)).toBeTruthy()
    expect(get).toHaveBeenCalledWith('/boss/5/drop-items/acquisitions?characterId=1')
    expect(screen.getByRole('checkbox', { name: `${dropItem.itemName} 획득 여부` })).toHaveProperty('checked', false)
  })

  it('deletes only the latest acquisition when an item has history', async () => {
    let acquisitions = [
      { id: 20, characterId: 1, bossDropItemId: 10, acquiredDate: '2026-10-07', memo: null },
      { id: 10, characterId: 1, bossDropItemId: 10, acquiredDate: '2026-10-01', memo: null },
    ]
    vi.spyOn(client, 'get').mockImplementation(async () => ({
      data: { success: true, data: [{ dropItem, acquired: acquisitions.length > 0, acquisitions }] },
    } as AxiosResponse))
    const remove = vi.spyOn(client, 'delete').mockImplementation(async () => {
      acquisitions = acquisitions.slice(1)
      return {} as AxiosResponse
    })

    renderModal()

    expect(await screen.findByText('2회')).toBeTruthy()
    fireEvent.click(screen.getByRole('checkbox', { name: `${dropItem.itemName} 획득 여부` }))

    await waitFor(() => expect(remove).toHaveBeenCalledWith('/boss/item-acquisition/20'))
    await waitFor(() => expect(screen.queryByText('2회')).toBeNull())
    expect(screen.getByRole('checkbox', { name: `${dropItem.itemName} 획득 여부` })).toHaveProperty('checked', true)
  })

  it('registers an acquisition and refreshes the item status', async () => {
    let acquisitions: BossDropItemAcquisitionStatus['acquisitions'] = []
    vi.spyOn(client, 'get').mockImplementation(async () => ({
      data: { success: true, data: [{ dropItem, acquired: acquisitions.length > 0, acquisitions }] },
    } as AxiosResponse))
    const create = vi.spyOn(client, 'post').mockImplementation(async () => {
      acquisitions = [{ id: 30, characterId: 1, bossDropItemId: 10, acquiredDate: '2026-10-07', memo: null }]
      return { data: { success: true, data: acquisitions[0] } } as AxiosResponse
    })

    renderModal()

    const checkbox = await screen.findByRole('checkbox', { name: `${dropItem.itemName} 획득 여부` })
    fireEvent.click(checkbox)

    await waitFor(() => expect(create).toHaveBeenCalledWith('/boss/item-acquisition', expect.objectContaining({
      characterId: 1,
      bossDropItemId: 10,
    })))
    await waitFor(() => expect(checkbox).toHaveProperty('checked', true))
  })
})
