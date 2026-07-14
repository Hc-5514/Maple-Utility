import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { useAuthStore } from '../stores/authStore'
import type { ApiResponse, AuthLoginResponse, UserApiKey } from '../types'

type Provider = 'kakao' | 'nexon'

const getOAuthUrl = (provider: Provider) => {
  const redirectUri = encodeURIComponent(import.meta.env.VITE_REDIRECT_URI ?? '')
  if (provider === 'kakao') {
    return `https://kauth.kakao.com/oauth/authorize?client_id=${import.meta.env.VITE_KAKAO_CLIENT_ID}&redirect_uri=${redirectUri}&response_type=code&state=kakao`
  }
  return `https://openapi.nexon.com/oauth2.0/authorize?client_id=${import.meta.env.VITE_NEXON_CLIENT_ID}&redirect_uri=${redirectUri}&response_type=code&state=nexon`
}

export default function LoginPage() {
  const [loading, setLoading] = useState<Provider | null>(null)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const { setUser, setHasApiKey } = useAuthStore()
  const isMock = import.meta.env.VITE_USE_MOCK === 'true'

  const handleMockLogin = async (provider: Provider) => {
    setLoading(provider)
    setError(null)
    try {
      const { data } = await client.post<ApiResponse<AuthLoginResponse>>(`/auth/${provider}`, {})
      localStorage.setItem('accessToken', data.data.accessToken)
      setUser(data.data.user)
      try {
        const { data: keyData } = await client.get<ApiResponse<UserApiKey>>('/user-api-keys')
        if (keyData.data.keyStatus === 'ACTIVE') {
          setHasApiKey(true)
          navigate('/dashboard', { replace: true })
          return
        }
      } catch {}
      navigate('/settings', { replace: true })
    } catch {
      setError('로그인 중 오류가 발생했습니다. 다시 시도해 주세요.')
    } finally {
      setLoading(null)
    }
  }

  const handleRealLogin = (provider: Provider) => {
    sessionStorage.setItem('oauth_provider', provider)
    window.location.href = getOAuthUrl(provider)
  }

  const handleLogin = (provider: Provider) => {
    if (isMock) void handleMockLogin(provider)
    else handleRealLogin(provider)
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#1a1a2e]">
      <div className="w-full max-w-sm rounded-xl bg-[#2d2d44] p-8 shadow-xl">
        <div className="mb-8 text-center">
          <p className="mb-3 text-5xl">🍁</p>
          <h1 className="text-2xl font-bold text-white">메이플 유틸리티</h1>
          <p className="mt-1 text-sm text-white/40">보스 스케줄 · 사냥 기록 · 통계</p>
        </div>

        <div className="space-y-3">
          <button
            onClick={() => handleLogin('kakao')}
            disabled={loading !== null}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#FEE500] py-3 text-sm font-semibold text-[#191919] transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {loading === 'kakao' ? (
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-[#191919] border-t-transparent" />
            ) : (
              <span>💬</span>
            )}
            카카오로 로그인
          </button>

          <button
            onClick={() => handleLogin('nexon')}
            disabled={loading !== null}
            className="flex w-full items-center justify-center gap-2 rounded-lg border border-white/20 bg-[#1a1a2e] py-3 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {loading === 'nexon' ? (
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
            ) : (
              <span>🎮</span>
            )}
            넥슨으로 로그인
          </button>
        </div>

        {error && (
          <p className="mt-4 text-center text-sm text-[#f87171]">{error}</p>
        )}

        {isMock && (
          <p className="mt-5 text-center text-xs text-white/25">MSW Mock 모드 — 실제 OAuth 없이 즉시 로그인</p>
        )}
      </div>
    </div>
  )
}
