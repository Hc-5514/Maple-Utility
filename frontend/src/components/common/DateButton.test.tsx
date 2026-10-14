// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import DateButton from './DateButton'

afterEach(cleanup)

describe('DateButton', () => {
  it('focuses the hidden date input before opening the native picker', () => {
    const calls: string[] = []
    const focus = vi.spyOn(HTMLInputElement.prototype, 'focus').mockImplementation(() => { calls.push('focus') })
    ;(HTMLInputElement.prototype as unknown as { showPicker: () => void }).showPicker = () => { calls.push('showPicker') }
    render(<DateButton label="시작일" value="2026-10-01" onChange={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '시작일 2026-10-01' }))

    expect(calls).toEqual(['focus', 'showPicker'])
    focus.mockRestore()
    delete (HTMLInputElement.prototype as unknown as { showPicker?: () => void }).showPicker
  })

  it('reports the selected date and releases focus so the picker closes', () => {
    const onChange = vi.fn()
    render(<DateButton label="종료일" value="2026-10-01" min="2026-09-01" max="2026-10-31" onChange={onChange} />)
    const input = screen.getByLabelText('종료일') as HTMLInputElement
    input.focus()
    const blur = vi.spyOn(input, 'blur')

    fireEvent.change(input, { target: { value: '2026-10-05' } })

    expect(onChange).toHaveBeenCalledWith('2026-10-05')
    expect(blur).toHaveBeenCalled()
    expect(input.min).toBe('2026-09-01')
    expect(input.max).toBe('2026-10-31')
  })
})
