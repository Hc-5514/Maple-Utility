import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'

interface Props {
  requireApiKey?: boolean
}

export default function PrivateRoute({ requireApiKey = false }: Props) {
  const { user, hasApiKey, isInitializing } = useAuthStore()

  if (isInitializing) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#1a1a2e]">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#4ade80] border-t-transparent" />
      </div>
    )
  }

  if (!user) return <Navigate to="/login" replace />
  if (requireApiKey && !hasApiKey) return <Navigate to="/settings" replace />
  return <Outlet />
}
