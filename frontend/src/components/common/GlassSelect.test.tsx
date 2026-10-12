// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import GlassSelect from './GlassSelect'

afterEach(cleanup)

const options = [
  { value: null, label: '전체' },
  { value: 1, label: '꼬농' },
  { value: 2, label: '말랑꼬농' },
]

describe('GlassSelect', () => {
  it('opens a listbox, marks the selected option and selects by click', () => {
    const onChange = vi.fn()
    render(<GlassSelect label="캐릭터 필터" value={null} options={options} onChange={onChange} />)

    const trigger = screen.getByRole('button', { name: '캐릭터 필터: 전체' })
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    fireEvent.click(trigger)
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByRole('option', { name: '전체' }).getAttribute('aria-selected')).toBe('true')

    fireEvent.click(screen.getByRole('option', { name: '말랑꼬농' }))
    expect(onChange).toHaveBeenCalledWith(2)
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('supports keyboard selection and Escape', () => {
    const onChange = vi.fn()
    render(<GlassSelect label="캐릭터 필터" value={null} options={options} onChange={onChange} />)

    const trigger = screen.getByRole('button', { name: '캐릭터 필터: 전체' })
    fireEvent.keyDown(trigger, { key: 'ArrowDown' })
    const listbox = screen.getByRole('listbox', { name: '캐릭터 필터' })
    fireEvent.keyDown(listbox, { key: 'ArrowDown' })
    fireEvent.keyDown(listbox, { key: 'Enter' })
    expect(onChange).toHaveBeenCalledWith(1)

    fireEvent.click(trigger)
    fireEvent.keyDown(screen.getByRole('listbox'), { key: 'Escape' })
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(document.activeElement).toBe(trigger)
  })

  it('closes on outside click and shows a custom trigger label', () => {
    render(
      <GlassSelect label="표시 건수" value={10} triggerLabel="10개씩 보기" options={[{ value: 10, label: '10개씩' }]} onChange={vi.fn()} />,
    )
    fireEvent.click(screen.getByRole('button', { name: '표시 건수: 10개씩 보기' }))
    expect(screen.getByRole('listbox')).toBeTruthy()
    fireEvent.mouseDown(document.body)
    expect(screen.queryByRole('listbox')).toBeNull()
  })
})
