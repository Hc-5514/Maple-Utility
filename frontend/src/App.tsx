import { useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import client from './api/client'
import type { ApiResponse, User, UserApiKey } from './types'
import { useAuthStore } from './stores/authStore'
import PrivateRoute from './router/PrivateRoute'
import Layout from './components/layout/Layout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import DashboardPage from './pages/DashboardPage'
import CharacterDetailPage from './pages/CharacterDetailPage'
import HuntingPage from './pages/HuntingPage'
import HuntingNewPage from './pages/HuntingNewPage'
import HuntingEditPage from './pages/HuntingEditPage'
import StatsPage from './pages/StatsPage'
import SettingsPage from './pages/SettingsPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 1000 * 60,
    },
  },
})

function AppRoutes() {
  const { setUser, setHasApiKey, setInitializing } = useAuthStore()

  useEffect(() => {
    const init = async () => {
      const token = localStorage.getItem('accessToken')
      if (!token) {
        setInitializing(false)
        return
      }
      try {
        const { data: userRes } = await client.get<ApiResponse<User>>('/users/me')
        setUser(userRes.data)
        try {
          const { data: keyRes } = await client.get<ApiResponse<UserApiKey>>('/user-api-keys')
          if (keyRes.data.keyStatus === 'ACTIVE') setHasApiKey(true)
        } catch {
          // API key 미등록 — 정상 케이스
        }
      } catch {
        localStorage.removeItem('accessToken')
      } finally {
        setInitializing(false)
      }
    }
    void init()
  }, [setUser, setHasApiKey, setInitializing])

  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />

      {/* 로그인 필요 */}
      <Route element={<PrivateRoute />}>
        <Route element={<Layout />}>
          <Route path="/hunting" element={<HuntingPage />} />
          <Route path="/hunting/new" element={<HuntingNewPage />} />
          <Route path="/hunting/:id/edit" element={<HuntingEditPage />} />
          <Route path="/stats" element={<StatsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      {/* 로그인 + API키 필요 */}
      <Route element={<PrivateRoute requireApiKey />}>
        <Route element={<Layout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/dashboard/:characterId" element={<CharacterDetailPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
