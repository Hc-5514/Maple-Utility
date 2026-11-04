// @vitest-environment jsdom
import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import { saveBossPeriod } from '../../hooks/useCharacterDetail'
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
  maxPartySize: 3,
  crystalPrice: 48_900_000,
  savedAt: null,
  items: [
    {
      dropItem: {
        id: 10, bossId: 5, itemName: '강렬한 힘의 결정', itemImage: null,
        itemDescription: null, dropRateTier: 'HIGH', itemKind: 'CRYSTAL', defaultQuantity: 1, detailOptions: [],
      },
      acquired: false, quantity: 1, mesoAmount: 48_900_000, detailItem: null,
    },
    {
      dropItem: {
        id: 11, bossId: 5, itemName: '솔 에르다의 기운', itemImage: null,
        itemDescription: null, dropRateTier: null, itemKind: 'FIXED', defaultQuantity: 3, detailOptions: [],
      },
      acquired: false, quantity: 3, mesoAmount: null, detailItem: null,
    },
  ],
}

function renderModal() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function Wrapper() {
    const [draft, setDraft] = useState<BossPeriodDraft>()
    return (
      <BossDropModal
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
    fireEvent.click(screen.getByRole('button', { name: /^파티 인원/ }))
    fireEvent.click(screen.getByRole('option', { name: '2명' }))
    await waitFor(() => expect(screen.getByLabelText('결정 금액').textContent).toBe('24,450,000'))
    fireEvent.click(screen.getByRole('checkbox', { name: '강렬한 힘의 결정 획득 여부' }))
    expect(put).not.toHaveBeenCalled()
  })

  it('limits party size options to the boss max and shows the crystal meso as plain text', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)

    renderModal()

    await screen.findByText('강렬한 힘의 결정')
    fireEvent.click(screen.getByRole('button', { name: /^파티 인원/ }))
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['1명', '2명', '3명'])
    expect(screen.getByLabelText('결정 금액').tagName).toBe('SPAN')
    expect(screen.getByLabelText('결정 금액').textContent).toBe('48,900,000')
  })

  it('edits the crystal amount with the pencil and keeps it when the party size changes', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)

    renderModal()

    await screen.findByText('강렬한 힘의 결정')
    fireEvent.click(screen.getByRole('button', { name: /^파티 인원/ }))
    fireEvent.click(screen.getByRole('option', { name: '2명' }))
    await waitFor(() => expect(screen.getByLabelText('결정 금액').textContent).toBe('24,450,000'))

    fireEvent.click(screen.getByRole('button', { name: '결정 금액 수정' }))
    const input = screen.getByLabelText('결정 금액 입력') as HTMLInputElement
    expect(input.value).toBe('24,450,000')
    fireEvent.change(input, { target: { value: '10,000,000' } })
    expect(input.value).toBe('10,000,000')
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(screen.getByLabelText('결정 금액').textContent).toBe('10,000,000')
    // 인원 분배 금액 10,000,000 × 2명 = 결정 가격 20,000,000
    fireEvent.click(screen.getByRole('button', { name: /^파티 인원/ }))
    fireEvent.click(screen.getByRole('option', { name: '1명' }))
    await waitFor(() => expect(screen.getByLabelText('결정 금액').textContent).toBe('20,000,000'))
  })

  it('cancels the crystal amount edit with Escape and commits on blur', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)

    renderModal()

    await screen.findByText('강렬한 힘의 결정')
    fireEvent.click(screen.getByRole('button', { name: '결정 금액 수정' }))
    fireEvent.change(screen.getByLabelText('결정 금액 입력'), { target: { value: '1' } })
    fireEvent.keyDown(screen.getByLabelText('결정 금액 입력'), { key: 'Escape' })
    expect(screen.getByLabelText('결정 금액').textContent).toBe('48,900,000')

    fireEvent.click(screen.getByRole('button', { name: '결정 금액 수정' }))
    fireEvent.change(screen.getByLabelText('결정 금액 입력'), { target: { value: '5000' } })
    fireEvent.blur(screen.getByLabelText('결정 금액 입력'))
    expect(screen.getByLabelText('결정 금액').textContent).toBe('5,000')
  })

  it('lets the user enter a price for a boss whose crystal price is unknown', async () => {
    const unknown: BossPeriod = {
      ...period,
      crystalPrice: 0,
      items: period.items.map((item) => item.dropItem.itemKind === 'CRYSTAL' ? { ...item, mesoAmount: null } : item),
    }
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: unknown } } as AxiosResponse)

    renderModal()

    expect((await screen.findByLabelText('결정 금액')).textContent).toBe('가격 미확인')
    fireEvent.click(await screen.findByRole('button', { name: '결정 금액 수정' }))
    fireEvent.change(screen.getByLabelText('결정 금액 입력'), { target: { value: '30000000' } })
    fireEvent.keyDown(screen.getByLabelText('결정 금액 입력'), { key: 'Enter' })
    expect(screen.getByLabelText('결정 금액').textContent).toBe('30,000,000')
    expect(screen.queryByText('가격 미확인')).toBeNull()
  })

  it('shows the detail select only for a checked box and clears the choice when unchecked', async () => {
    const boxPeriod: BossPeriod = {
      ...period,
      items: [
        ...period.items,
        {
          dropItem: {
            id: 12, bossId: 5, itemName: '홍옥의 보스 반지 상자', itemImage: null, itemDescription: null,
            dropRateTier: null, itemKind: 'RANDOM', defaultQuantity: 1,
            detailOptions: ['리스트레인트 링 Lv4', '컨티뉴어스링 Lv4', '기타'],
          },
          acquired: false, quantity: 1, mesoAmount: null, detailItem: null,
        },
      ],
    }
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: boxPeriod } } as AxiosResponse)

    renderModal()

    await screen.findByText('홍옥의 보스 반지 상자')
    expect(screen.queryByRole('button', { name: /^홍옥의 보스 반지 상자 상세/ })).toBeNull()
    fireEvent.click(screen.getByRole('checkbox', { name: '홍옥의 보스 반지 상자 획득 여부' }))
    fireEvent.click(screen.getByRole('button', { name: '홍옥의 보스 반지 상자 상세: 상세 선택' }))
    expect(screen.getAllByRole('option').map((option) => option.textContent))
      .toEqual(['리스트레인트 링 Lv4', '컨티뉴어스링 Lv4', '기타'])
    fireEvent.click(screen.getByRole('option', { name: '컨티뉴어스링 Lv4' }))
    expect(screen.getByRole('button', { name: '홍옥의 보스 반지 상자 상세: 컨티뉴어스링 Lv4' })).toBeTruthy()

    fireEvent.click(screen.getByRole('checkbox', { name: '홍옥의 보스 반지 상자 획득 여부' }))
    expect(screen.queryByRole('button', { name: /^홍옥의 보스 반지 상자 상세/ })).toBeNull()
    fireEvent.click(screen.getByRole('checkbox', { name: '홍옥의 보스 반지 상자 획득 여부' }))
    expect(screen.getByRole('button', { name: '홍옥의 보스 반지 상자 상세: 상세 선택' })).toBeTruthy()
  })

  it('does not offer a detail select for items without options', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)

    renderModal()

    await screen.findByText('솔 에르다의 기운')
    expect(screen.queryByRole('button', { name: /상세/ })).toBeNull()
  })

  it('sends the chosen detail item only for acquired items', async () => {
    const put = vi.spyOn(client, 'put').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)
    const box = {
      dropItem: {
        id: 12, bossId: 5, itemName: '홍옥의 보스 반지 상자', itemImage: null, itemDescription: null,
        dropRateTier: null, itemKind: 'RANDOM' as const, defaultQuantity: 1, detailOptions: ['기타'],
      },
      quantity: 1, mesoAmount: null,
    }

    await saveBossPeriod({
      ...period,
      items: [{ ...box, acquired: true, detailItem: '기타' }, { ...box, dropItem: { ...box.dropItem, id: 13 }, acquired: false, detailItem: '기타' }],
    })

    const body = put.mock.calls[0][1] as { items: { bossDropItemId: number; detailItem: string | null }[] }
    expect(body.items).toEqual([
      expect.objectContaining({ bossDropItemId: 12, detailItem: '기타' }),
      expect.objectContaining({ bossDropItemId: 13, detailItem: null }),
    ])
  })

  it('sends the edited crystal price when the period is saved', async () => {
    const put = vi.spyOn(client, 'put').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)

    await saveBossPeriod({ ...period, crystalPrice: 30_000_000 })

    expect(put).toHaveBeenCalledWith('/boss/5/period', expect.objectContaining({ crystalPrice: 30_000_000, partySize: 1 }))
  })

  it('selects fixed item quantity from 1 to the default quantity', async () => {
    vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: period } } as AxiosResponse)

    renderModal()

    await screen.findByText('솔 에르다의 기운')
    fireEvent.click(screen.getByRole('button', { name: /^솔 에르다의 기운 수량: 3/ }))
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['1', '2', '3'])
    fireEvent.click(screen.getByRole('option', { name: '2' }))
    expect(screen.getByRole('button', { name: /^솔 에르다의 기운 수량: 2/ })).toBeTruthy()
  })
})
