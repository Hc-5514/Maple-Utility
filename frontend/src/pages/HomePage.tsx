import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'

export default function HomePage() {
  const { user, isInitializing } = useAuthStore()
  if (isInitializing) return null
  return <Navigate to={user ? '/dashboard' : '/login'} replace />
}
