import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import client from '../api/client'
import { clearAuthSession } from '../api/authSession'
import { getSessionGeneration, isCurrentSession } from '../api/session'
import { useAuthStore } from '../stores/authStore'

export function useAuth() {
  const { user, hasApiKey, isInitializing } = useAuthStore()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const logout = async () => {
    const generation = getSessionGeneration()
    try {
      await client.post('/auth/logout')
    } catch {
      // 서버 로그아웃 실패 시에도 클라이언트 세션은 정리
    }
    if (!isCurrentSession(generation)) return
    await clearAuthSession(queryClient)
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
