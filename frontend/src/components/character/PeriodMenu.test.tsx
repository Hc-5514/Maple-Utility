// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PeriodMenu from './PeriodMenu'

const options = [
  { value: 'a', label: '리스트레인트 링 Lv4' },
  { value: 'b', label: '컨티뉴어스링 Lv4' },
  { value: 'c', label: '기타' },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function rect(top: number, bottom: number) {
  return { top, bottom, left: 0, right: 0, width: 0, height: bottom - top, x: 0, y: top, toJSON: () => ({}) } as DOMRect
}

describe('PeriodMenu', () => {
  it('shows the first option for an unmatched value unless a placeholder is given', () => {
    const { rerender } = render(<PeriodMenu compact label="상세" value="" options={options} onChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: '상세: 리스트레인트 링 Lv4' })).toBeTruthy()

    rerender(<PeriodMenu compact label="상세" value="" placeholder="상세 선택" options={options} onChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: '상세: 상세 선택' }).textContent).toContain('상세 선택')

    rerender(<PeriodMenu compact label="상세" value="c" placeholder="상세 선택" options={options} onChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: '상세: 기타' })).toBeTruthy()
  })

  it('opens downward by default and reports the chosen option', () => {
    const onChange = vi.fn()
    render(<PeriodMenu compact label="상세" value="" placeholder="상세 선택" options={options} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: '상세: 상세 선택' }))
    const listbox = screen.getByRole('listbox', { name: '상세' })
    expect(listbox.className).toContain('top-[44px]')
    expect(listbox.className).not.toContain('bottom-[44px]')
    fireEvent.click(screen.getByRole('option', { name: '컨티뉴어스링 Lv4' }))
    expect(onChange).toHaveBeenCalledWith('b')
  })

  it('opens upward when the scroll area has little room below and more room above', () => {
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
      if (this.classList.contains('dialog-scroll')) return rect(0, 300)
      if (this.classList.contains('relative')) return rect(230, 266)
      return rect(0, 0)
    })
    render(
      <div className="dialog-scroll">
        <PeriodMenu compact label="상세" value="" placeholder="상세 선택" options={options} onChange={vi.fn()} />
      </div>,
    )

    fireEvent.click(screen.getByRole('button', { name: '상세: 상세 선택' }))
    const listbox = screen.getByRole('listbox', { name: '상세' })
    expect(listbox.className).toContain('bottom-[44px]')
    expect(listbox.className).not.toContain('top-[44px]')
  })

  it('stays downward near the top of the scroll area even when the list is tall', () => {
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (this: Element) {
      if (this.classList.contains('dialog-scroll')) return rect(0, 120)
      if (this.classList.contains('relative')) return rect(10, 46)
      return rect(0, 0)
    })
    render(
      <div className="dialog-scroll">
        <PeriodMenu compact label="상세" value="" placeholder="상세 선택" options={options} onChange={vi.fn()} />
      </div>,
    )

    fireEvent.click(screen.getByRole('button', { name: '상세: 상세 선택' }))
    expect(screen.getByRole('listbox', { name: '상세' }).className).toContain('top-[44px]')
  })
})
