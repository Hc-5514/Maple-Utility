import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import client from '../api/client'
import { useAuthStore } from '../stores/authStore'
import type { ApiResponse, ApiKeyStatusResponse, AuthLoginResponse, User } from '../types'

export default function AuthCallbackPage() {
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { setUser, setHasApiKey } = useAuthStore()

  useEffect(() => {
    const code = searchParams.get('code')
    sessionStorage.removeItem('oauth_provider')

    if (!code) {
      setError('인가코드를 받지 못했습니다.')
      return
    }

    const login = async () => {
      try {
        const { data } = await client.post<ApiResponse<AuthLoginResponse>>(
          '/auth/kakao',
          { code },
        )
        localStorage.setItem('accessToken', data.data.accessToken)

        const { data: meRes } = await client.get<ApiResponse<User>>('/auth/me')
        setUser(meRes.data)

        let hasKey = false
        try {
          const { data: keyData } = await client.get<ApiResponse<ApiKeyStatusResponse>>('/api-key/status')
          if (keyData.data.registered && keyData.data.keyStatus === 'ACTIVE') {
            setHasApiKey(true)
            hasKey = true
          }
        } catch {}

        navigate(hasKey ? '/dashboard' : '/settings', { replace: true })
      } catch {
        setError('로그인 중 오류가 발생했습니다.')
      }
    }

    void login()
  }, [navigate, searchParams, setUser, setHasApiKey])

  if (error) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-[#1a1a2e]">
        <p className="text-[#f87171]">{error}</p>
        <button
          onClick={() => navigate('/login')}
          className="rounded-lg bg-[#4ade80] px-4 py-2 text-sm font-semibold text-black hover:opacity-90"
        >
          로그인 페이지로
        </button>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#1a1a2e]">
      <div className="flex items-center gap-3">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-[#4ade80] border-t-transparent" />
        <span className="text-white/70">로그인 처리 중...</span>
      </div>
    </div>
  )
}
