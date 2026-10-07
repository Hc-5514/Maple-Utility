// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
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

describe('ItemAcquisitionList', () => {
  it('shows one-based page labels and moves with zero-based API indexes', () => {
    const onPageChange = vi.fn()
    const onPageSizeChange = vi.fn()
    render(<ItemAcquisitionList page={firstPage} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} />)

    expect(screen.getByText('1 / 4')).toBeTruthy()
    expect((screen.getByRole('button', { name: '이전 페이지' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    expect(onPageChange).toHaveBeenCalledWith(1)
    fireEvent.change(screen.getByRole('combobox', { name: '페이지당 표시 건수' }), { target: { value: '20' } })
    expect(onPageSizeChange).toHaveBeenCalledWith(20)
  })

  it('disables next on the last page and hides pagination for no results', () => {
    const onPageChange = vi.fn()
    const onPageSizeChange = vi.fn()
    const view = render(<ItemAcquisitionList page={{ ...firstPage, page: 3 }} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} />)
    expect(screen.getByText('4 / 4')).toBeTruthy()
    expect((screen.getByRole('button', { name: '다음 페이지' }) as HTMLButtonElement).disabled).toBe(true)

    view.rerender(<ItemAcquisitionList page={{ content: [], totalElements: 0, totalPages: 0, page: 0, size: 10 }} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} />)
    expect(screen.getByText('획득 기록이 없습니다')).toBeTruthy()
    expect(screen.queryByRole('navigation', { name: '보스 아이템 획득 이력 페이지' })).toBeNull()
  })

  it('offers a first-page action when saved data makes the current page invalid', () => {
    const onPageChange = vi.fn()
    render(<ItemAcquisitionList
      page={{ content: [], totalElements: 9, totalPages: 1, page: 2, size: 10 }}
      onPageChange={onPageChange}
      onPageSizeChange={vi.fn()}
    />)
    fireEvent.click(screen.getByRole('button', { name: '첫 페이지' }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })
})
