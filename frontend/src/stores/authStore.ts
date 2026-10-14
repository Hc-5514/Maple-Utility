import { create } from 'zustand'
import type { User } from '../types'
import { advanceSession } from '../api/session'

interface AuthState {
  user: User | null
  hasApiKey: boolean
  isInitializing: boolean
  sessionRevision: number
  setUser: (user: User | null) => void
  setHasApiKey: (v: boolean) => void
  setInitializing: (v: boolean) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  hasApiKey: false,
  isInitializing: true,
  sessionRevision: 0,
  setUser: (user) => set({ user }),
  setHasApiKey: (hasApiKey) => set({ hasApiKey }),
  setInitializing: (isInitializing) => set({ isInitializing }),
  logout: () => {
    const sessionRevision = advanceSession(null)
    set({ user: null, hasApiKey: false, isInitializing: false, sessionRevision })
  },
}))
