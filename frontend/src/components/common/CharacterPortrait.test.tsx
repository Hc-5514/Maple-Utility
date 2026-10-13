// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import CharacterPortrait from './CharacterPortrait'

afterEach(cleanup)

describe('CharacterPortrait', () => {
  it('crops the transparent canvas margin by zooming into the centered character', () => {
    render(<CharacterPortrait src="https://example.com/look.png" alt="꼬농" size={72} />)
    const image = screen.getByRole('img', { name: '꼬농' })
    const frame = image.parentElement as HTMLElement
    expect(frame.style.width).toBe('72px')
    expect(frame.style.height).toBe('72px')
    expect(frame.className).toContain('overflow-hidden')
    expect(image.style.width).toBe('300%')
    expect(image.style.transform).toBe('translate(-50%, -55%)')
  })
})
