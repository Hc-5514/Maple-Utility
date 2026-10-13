// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { UnsavedChangesProvider } from '../../router/UnsavedChangesGuard'
import { useAuthStore } from '../../stores/authStore'
import Header from './Header'

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, hasApiKey: false })
})

describe('Header', () => {
  it('styles logout like the other menu items', () => {
    useAuthStore.setState({
      user: { id: 1, nickname: '메이플', email: null } as never,
      hasApiKey: true,
    })
    const router = createMemoryRouter([{
      path: '/',
      element: (
        <UnsavedChangesProvider>
          <Header />
        </UnsavedChangesProvider>
      ),
    }])
    render(<RouterProvider router={router} />)

    const logout = screen.getByRole('button', { name: '로그아웃' })
    const settings = screen.getByRole('link', { name: '설정' })
    for (const token of ['h-10', 'rounded-[10px]', 'px-4', 'items-center']) {
      expect(logout.className).toContain(token)
      expect(settings.className).toContain(token)
    }
  })
})
