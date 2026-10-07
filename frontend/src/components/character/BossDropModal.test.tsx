// @vitest-environment jsdom
import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import type { BossPeriod, BossPeriodDraft, SchedulerBossRecord } from '../../types'
import BossDropModal from './BossDropModal'

const record: SchedulerBossRecord = {
  characterId: 1,
  bossId: 5,
  bossName: '스우',
  difficulty: 'HARD',
  resetPeriod: 'WEEKLY',
  isCompleted: true,
  syncedAt: '2026-10-08T01:00:00',
}

const period: BossPeriod = {
  bossId: 5,
  characterId: 1,
  periodStart: '2026-10-08',
  partySize: 1,
  crystalPrice: 48_900_000,
  savedAt: null,
  items: [
    {
      dropItem: {
        id: 10, bossId: 5, itemName: '강렬한 힘의 결정', itemImage: null,
        itemDescription: null, dropRateTier: 'HIGH', itemKind: 'CRYSTAL', defaultQuantity: 1,
      },
      acquired: false, quantity: 1, mesoAmount: 48_900_000,
    },
    {
      dropItem: {
        id: 11, bossId: 5, itemName: '솔 에르다의 기운', itemImage: null,
        itemDescription: null, dropRateTier: null, itemKind: 'FIXED', defaultQuantity: 3,
      },
      acquired: false, quantity: 3, mesoAmount: null,
    },
  ],
}

function renderModal() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function Wrapper() {
    const [draft, setDraft] = useState<BossPeriodDraft>()
    return (
      <BossDropModal
        isOpen
        onClose={vi.fn()}
        record={record}
        characterId={1}
        periodStart="2026-10-08"
        draft={draft}
        onLoaded={(loaded) => setDraft((previous) => previous ?? { ...loaded, dirty: false })}
        onChange={setDraft}
      />
    )
  }
  render(<QueryClientProvider client={queryClient}><Wrapper /></QueryClientProvider>)
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('BossDropModal', () => {
  it('loads a period and edits party share without a write request', async () => {
    const get = vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)
    const put = vi.spyOn(client, 'put')

    renderModal()

    expect(await screen.findByText('강렬한 힘의 결정')).toBeTruthy()
    expect(get).toHaveBeenCalledWith('/boss/5/period?characterId=1&periodStart=2026-10-08')
    fireEvent.change(screen.getByLabelText('파티 인원'), { target: { value: '2' } })
    await waitFor(() => expect(screen.getByLabelText('결정 금액')).toHaveProperty('value', '24450000'))
    fireEvent.click(screen.getByRole('checkbox', { name: '강렬한 힘의 결정 획득 여부' }))
    expect(put).not.toHaveBeenCalled()
  })

  it('uses the fixed item default quantity', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)

    renderModal()

    expect(await screen.findByLabelText('솔 에르다의 기운 수량')).toHaveProperty('value', '3')
  })
})
