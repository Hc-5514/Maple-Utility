import { useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  createBrowserRouter,
  createRoutesFromElements,
  Outlet,
  Route,
  RouterProvider,
} from 'react-router-dom'
import { clearAuthSession, loadSessionIdentity } from './api/authSession'
import { getSessionGeneration, isCurrentSession } from './api/session'
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
  const { setInitializing } = useAuthStore()

  useEffect(() => {
    const init = async () => {
      const generation = getSessionGeneration()
      const token = localStorage.getItem('accessToken')
      if (!token) {
        setInitializing(false)
        return
      }
      try {
        await loadSessionIdentity(queryClient)
      } catch {
        if (isCurrentSession(generation)) await clearAuthSession(queryClient)
      } finally {
        if (isCurrentSession(generation)) setInitializing(false)
      }
    }
    void init()
  }, [setInitializing])

  return <Outlet />
}

function SessionLayout() {
  const sessionRevision = useAuthStore((state) => state.sessionRevision)
  return <Layout key={sessionRevision} />
}

const routes = (
  <Route element={<AppRoutes />}>
    <Route path="/" element={<HomePage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/auth/callback" element={<AuthCallbackPage />} />

    {/* 로그인 필요 */}
    <Route element={<PrivateRoute />}>
      <Route element={<SessionLayout />}>
        <Route path="/hunting" element={<HuntingPage />} />
        <Route path="/hunting/new" element={<HuntingNewPage />} />
        <Route path="/hunting/:id/edit" element={<HuntingEditPage />} />
        <Route path="/stats" element={<StatsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Route>
    </Route>

    {/* 로그인 + API키 필요 */}
    <Route element={<PrivateRoute requireApiKey />}>
      <Route element={<SessionLayout />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/dashboard/:characterId" element={<CharacterDetailPage />} />
      </Route>
    </Route>
  </Route>
)

const router = createBrowserRouter(createRoutesFromElements(routes))

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  )
}
