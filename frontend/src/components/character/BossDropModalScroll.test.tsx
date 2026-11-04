// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import type { BossPeriod, SchedulerBossRecord } from '../../types'
import BossDropModal from './BossDropModal'

const record: SchedulerBossRecord = {
  characterId: 1, bossId: 5, bossName: '스우', difficulty: 'HARD', resetPeriod: 'WEEKLY',
  isCompleted: true, syncedAt: '2026-10-08T01:00:00',
}

const period: BossPeriod = {
  bossId: 5, characterId: 1, periodStart: '2026-10-08', partySize: 1, maxPartySize: 3,
  crystalPrice: 48_900_000, savedAt: null,
  // 상자 상세 필드(detailOptions, detailItem) 추가 여부와 무관하게 쓰도록 목록 형태로 단언
  items: ['강렬한 힘의 결정', '아이템 A', '아이템 B'].map((itemName, index) => ({
    dropItem: {
      id: 10 + index, bossId: 5, itemName, itemImage: null, itemDescription: null, dropRateTier: null,
      itemKind: index === 0 ? 'CRYSTAL' : 'RANDOM', defaultQuantity: 1, detailOptions: [],
    },
    acquired: false, quantity: 1, mesoAmount: index === 0 ? 48_900_000 : null, detailItem: null,
  })) as unknown as BossPeriod['items'],
}

function renderModal() {
  vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <BossDropModal
        onClose={vi.fn()} record={record} characterId={1} periodStart="2026-10-08"
        draft={undefined} onLoaded={vi.fn()} onChange={vi.fn()}
      />
    </QueryClientProvider>,
  )
}

function mockListHeights(scrollHeight: number, clientHeight: number) {
  vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(scrollHeight)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(clientHeight)
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('BossDropModal item list scroll gutter', () => {
  it('keeps a 14px gap between the content and the scrollbar when the list scrolls', async () => {
    mockListHeights(700, 512)
    renderModal()

    const list = (await screen.findByText('아이템 A')).closest('ul') as HTMLElement
    await waitFor(() => expect(list.className).toContain('pr-[14px]'))
  })

  it('does not reserve a gap when the list fits without scrolling', async () => {
    mockListHeights(171, 171)
    renderModal()

    const list = (await screen.findByText('아이템 A')).closest('ul') as HTMLElement
    expect(list.className).not.toContain('pr-[14px]')
  })

  it('draws the top divider on the first row so it matches the row width', async () => {
    mockListHeights(171, 171)
    renderModal()

    const first = (await screen.findByText('강렬한 힘의 결정')).closest('li') as HTMLElement
    expect(first.className).toContain('first:border-t')
    expect((first.closest('ul') as HTMLElement).className).not.toContain('border-t')
  })
})
