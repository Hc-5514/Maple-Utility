import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { useAuthStore } from '../stores/authStore'

export function useAuth() {
  const { user, hasApiKey, isInitializing, logout: storeLogout } = useAuthStore()
  const navigate = useNavigate()

  const logout = async () => {
    try {
      await client.post('/auth/logout')
    } catch {
      // 서버 로그아웃 실패 시에도 클라이언트 세션은 정리
    }
    storeLogout()
    navigate('/login')
  }

  return {
    user,
    hasApiKey,
    isInitializing,
    isLoggedIn: user !== null,
    logout,
  }
}
