// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import HuntingForm from './HuntingForm'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const favorites = [
  { id: 1, characterName: '꼬농', worldName: '엘리시움' },
  { id: 2, characterName: '말랑꼬농', worldName: '엘리시움' },
  { id: 3, characterName: '스카궁수', worldName: '스카니아' },
]
const grounds = [
  { id: 10, regionName: '탈라하트', mapName: '환영의 길 4', maxMonsterLevel: 294, favorite: false },
  { id: 11, regionName: '탈라하트', mapName: '환영의 길 3', maxMonsterLevel: 294, favorite: false },
  { id: 20, regionName: '고통의 미궁', mapName: '고통의 미궁 중심부 6', maxMonsterLevel: 258, favorite: false },
]

async function renderForm(onSubmit = vi.fn(), latest: unknown = null) {
  vi.spyOn(client, 'get').mockImplementation(async (url) => {
    const path = String(url)
    const data = path === '/characters/favorites' ? favorites : path === '/hunting/grounds' ? grounds : latest
    return { data: { success: true, data } } as AxiosResponse
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <HuntingForm submitLabel="등록" isSubmitting={false} onSubmit={onSubmit} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  await waitFor(() => expect(queryClient.isFetching()).toBe(0))
  return onSubmit
}

const options = (label: string) => {
  fireEvent.click(screen.getByRole('button', { name: new RegExp(`^${label}:`) }))
  return screen.getAllByRole('option').map((option) => option.textContent)
}
const pick = (label: string, option: string) => {
  fireEvent.click(screen.getByRole('button', { name: new RegExp(`^${label}:`) }))
  fireEvent.click(screen.getByRole('option', { name: option }))
}

describe('HuntingForm', () => {
  it('lists only worlds of favorite characters and filters characters by world', async () => {
    await renderForm()
    expect(options('월드 선택')).toEqual(['월드 선택', '스카니아', '엘리시움'])
    fireEvent.click(screen.getByRole('option', { name: '엘리시움' }))

    expect(options('캐릭터 선택')).toEqual(['캐릭터 선택', '꼬농', '말랑꼬농'])
    fireEvent.click(screen.getByRole('option', { name: '말랑꼬농' }))

    pick('월드 선택', '스카니아')
    expect(screen.getByRole('button', { name: '캐릭터 선택: 캐릭터 선택' })).toBeTruthy()
    expect(options('캐릭터 선택')).toEqual(['캐릭터 선택', '스카궁수'])
  })

  it('fills the world from the selected character', async () => {
    await renderForm()
    expect(options('캐릭터 선택')).toHaveLength(4)
    fireEvent.click(screen.getByRole('option', { name: '스카궁수' }))
    expect(screen.getByRole('button', { name: '월드 선택: 스카니아' })).toBeTruthy()
  })

  it('filters hunting grounds by region and resets the ground on region change', async () => {
    const onSubmit = await renderForm()
    expect(options('지역')).toEqual(['지역 선택', '탈라하트', '고통의 미궁'])
    fireEvent.click(screen.getByRole('option', { name: '탈라하트' }))

    expect(options('사냥터')).toEqual(['사냥터 선택', '환영의 길 4 · Lv.294', '환영의 길 3 · Lv.294'])
    fireEvent.click(screen.getByRole('option', { name: '환영의 길 3 · Lv.294' }))

    pick('지역', '고통의 미궁')
    expect(screen.getByRole('button', { name: '사냥터: 사냥터 선택' })).toBeTruthy()
    pick('사냥터', '고통의 미궁 중심부 6 · Lv.258')

    pick('캐릭터 선택', '꼬농')
    fireEvent.change(screen.getByLabelText('솔 에르다 조각'), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      characterId: 1,
      solErdaEarned: 12,
      huntingGroundId: 20,
      huntingGround: '고통의 미궁 중심부 6',
    }))
  })

  it('defaults region and ground to the character latest ground until the user picks one', async () => {
    await renderForm(vi.fn(), grounds[1])
    expect(options('캐릭터 선택')).toHaveLength(4)
    fireEvent.click(screen.getByRole('option', { name: '꼬농' }))
    expect(await screen.findByRole('button', { name: '지역: 탈라하트' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '사냥터: 환영의 길 3 · Lv.294' })).toBeTruthy()
  })

  it('requires a character and at least one reward', async () => {
    const onSubmit = await renderForm()
    fireEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(screen.getByRole('alert').textContent).toBe('캐릭터를 선택해 주세요.')

    expect(options('캐릭터 선택')).toHaveLength(4)
    fireEvent.click(screen.getByRole('option', { name: '꼬농' }))
    fireEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(screen.getByRole('alert').textContent).toBe('메소 또는 솔 에르다 조각 중 최소 하나를 입력해 주세요.')
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
