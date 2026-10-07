// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import CatalogImage, { catalogImageSrc } from './CatalogImage'

afterEach(cleanup)

describe('CatalogImage', () => {
  it('does not request an unregistered public asset', () => {
    expect(catalogImageSrc('/assets/boss/suu.png')).toBeNull()
    render(<CatalogImage src="/assets/boss/suu.png" alt="스우" kind="boss" />)
    expect(screen.getByRole('img', { name: '스우' }).tagName).toBe('DIV')
  })

  it('falls back when a remote image fails', () => {
    render(<CatalogImage src="https://example.com/item.png" alt="결정" kind="item" />)
    fireEvent.error(screen.getByRole('img', { name: '결정' }))
    expect(screen.getByRole('img', { name: '결정' }).tagName).toBe('DIV')
  })

  it('rejects insecure or arbitrary image URLs', () => {
    expect(catalogImageSrc('http://example.com/item.png')).toBeNull()
    expect(catalogImageSrc('javascript:alert(1)')).toBeNull()
  })
})
