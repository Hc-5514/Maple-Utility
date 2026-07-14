import { create } from 'zustand'
import type { User } from '../types'

interface AuthState {
  user: User | null
  hasApiKey: boolean
  isInitializing: boolean
  setUser: (user: User | null) => void
  setHasApiKey: (v: boolean) => void
  setInitializing: (v: boolean) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  hasApiKey: false,
  isInitializing: true,
  setUser: (user) => set({ user }),
  setHasApiKey: (hasApiKey) => set({ hasApiKey }),
  setInitializing: (isInitializing) => set({ isInitializing }),
  logout: () => {
    localStorage.removeItem('accessToken')
    set({ user: null, hasApiKey: false })
  },
}))
