// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import client from '../../api/client'
import CharacterChangeModal from './CharacterChangeModal'

const characters = [
  { id: 1, characterName: '꼬농', worldName: '엘리시움', characterClass: '아크메이지(썬,콜)', characterLevel: 294, characterImage: null, favorite: true },
  { id: 2, characterName: '말랑꼬농', worldName: '엘리시움', characterClass: '윈드브레이커', characterLevel: 285, characterImage: null, favorite: true },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderModal(onClose = vi.fn(), onChange = vi.fn()) {
  vi.spyOn(client, 'get').mockResolvedValue({ data: { success: true, data: characters } } as AxiosResponse)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <CharacterChangeModal currentId={1} onClose={onClose} onChange={onChange} />
    </QueryClientProvider>,
  )
  return { onClose, onChange }
}

describe('CharacterChangeModal', () => {
  it('marks the current favorite and changes to the newly selected one', async () => {
    const { onChange } = renderModal()
    expect((await screen.findByRole('radio', { name: '꼬농' })).getAttribute('aria-checked')).toBe('true')
    const confirm = screen.getByRole('button', { name: '변경' })
    fireEvent.click(screen.getByRole('radio', { name: '말랑꼬농' }))
    fireEvent.click(confirm)
    expect(onChange).toHaveBeenCalledWith(2)
  })

  it('closes with cancel, the close button and Escape', async () => {
    const { onClose } = renderModal()
    await screen.findByRole('radio', { name: '꼬농' })
    fireEvent.click(screen.getByRole('button', { name: '취소' }))
    fireEvent.click(screen.getByRole('button', { name: '닫기' }))
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(3)
  })
})
