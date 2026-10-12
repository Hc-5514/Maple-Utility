import { create } from 'zustand'
type Theme = 'light' | 'dark'
function savedTheme(): Theme {
  try {
    return window.localStorage.getItem('maple-theme') === 'light' ? 'light' : 'dark'
  } catch {
    return 'dark'
  }
}
export const useThemeStore = create<{ theme: Theme; setTheme: (theme: Theme) => void }>((set) => ({
  theme: savedTheme(),
  setTheme: (theme) => {
    try {
      window.localStorage.setItem('maple-theme', theme)
    } catch {
      /* Storage unavailable; retain session theme. */
    }
    set({ theme })
  },
}))
