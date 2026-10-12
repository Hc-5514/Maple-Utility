// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ItemAcquisitionList from './ItemAcquisitionList'
import type { PageResponse, StatsBossItem } from '../../types'

const item: StatsBossItem = {
  acquiredDate: '2026-10-07',
  characterName: '꼬농',
  bossName: '스우',
  difficulty: 'HARD',
  itemName: '보스 아이템',
}

const firstPage: PageResponse<StatsBossItem> = {
  content: [item], totalElements: 40, totalPages: 4, page: 0, size: 10,
}

afterEach(cleanup)

function renderList(page: PageResponse<StatsBossItem>, handlers = {
  onBossChange: vi.fn(), onPageChange: vi.fn(), onPageSizeChange: vi.fn(),
}) {
  const view = render(
    <ItemAcquisitionList page={page} bossName={null} bosses={['자쿰', '스우']} {...handlers} />,
  )
  return { ...handlers, view }
}

describe('ItemAcquisitionList', () => {
  it('shows the total, difficulty badge and moves with zero-based page indexes', () => {
    const { onPageChange, onPageSizeChange } = renderList(firstPage)

    expect(screen.getByText('총 40개')).toBeTruthy()
    expect(screen.getByText('하드')).toBeTruthy()
    const nav = screen.getByRole('navigation', { name: '보스 아이템 획득 이력 페이지' })
    expect((within(nav).getByRole('button', { name: '이전' }) as HTMLButtonElement).disabled).toBe(true)
    expect(within(nav).getByRole('button', { name: '1' }).getAttribute('aria-current')).toBe('page')
    fireEvent.click(within(nav).getByRole('button', { name: '다음' }))
    expect(onPageChange).toHaveBeenCalledWith(1)

    fireEvent.click(screen.getByRole('button', { name: '표시 건수: 10개씩 보기' }))
    fireEvent.click(screen.getByRole('option', { name: '20개씩' }))
    expect(onPageSizeChange).toHaveBeenCalledWith(20)
  })

  it('lists all bosses in the boss filter and reports the choice', () => {
    const { onBossChange } = renderList(firstPage)

    fireEvent.click(screen.getByRole('button', { name: '보스 필터: 전체 보스' }))
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['전체 보스', '자쿰', '스우'])
    fireEvent.click(screen.getByRole('option', { name: '스우' }))
    expect(onBossChange).toHaveBeenCalledWith('스우')
  })

  it('disables next on the last page and hides pagination for no results', () => {
    const { view, ...handlers } = renderList({ ...firstPage, page: 3 })
    const nav = screen.getByRole('navigation', { name: '보스 아이템 획득 이력 페이지' })
    expect((within(nav).getByRole('button', { name: '다음' }) as HTMLButtonElement).disabled).toBe(true)

    view.rerender(
      <ItemAcquisitionList
        page={{ content: [], totalElements: 0, totalPages: 0, page: 0, size: 10 }}
        bossName={null}
        bosses={[]}
        {...handlers}
      />,
    )
    expect(screen.getByText('획득 기록이 없습니다')).toBeTruthy()
    expect(screen.queryByRole('navigation', { name: '보스 아이템 획득 이력 페이지' })).toBeNull()
  })

  it('offers a first-page action when saved data makes the current page invalid', () => {
    const { onPageChange } = renderList({ content: [], totalElements: 9, totalPages: 1, page: 2, size: 10 })
    fireEvent.click(screen.getByRole('button', { name: '첫 페이지' }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })
})
