import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { useAuthStore } from '../stores/authStore'
import type { ApiKeyStatusResponse, ApiResponse, AuthLoginResponse, User } from '../types'

const getKakaoOAuthUrl = () => {
  const redirectUri = encodeURIComponent(import.meta.env.VITE_REDIRECT_URI ?? '')
  return `https://kauth.kakao.com/oauth/authorize?client_id=${import.meta.env.VITE_KAKAO_CLIENT_ID}&redirect_uri=${redirectUri}&response_type=code&state=kakao`
}

const afterLogin = async (
  data: AuthLoginResponse,
  setUser: (u: User | null) => void,
  setHasApiKey: (v: boolean) => void,
) => {
  localStorage.setItem('accessToken', data.accessToken)
  const { data: meRes } = await client.get<ApiResponse<User>>('/auth/me')
  setUser(meRes.data)
  try {
    const { data: keyData } = await client.get<ApiResponse<ApiKeyStatusResponse>>('/api-key/status')
    if (keyData.data.registered && keyData.data.keyStatus === 'ACTIVE') {
      setHasApiKey(true)
      return true
    }
  } catch {}
  return false
}

export default function LoginPage() {
  const [kakaoLoading, setKakaoLoading] = useState(false)
  const [apiKey, setApiKey] = useState('')
  const [apiKeyLoading, setApiKeyLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const { setUser, setHasApiKey } = useAuthStore()
  const isMock = import.meta.env.VITE_USE_MOCK === 'true'

  const handleKakaoLogin = async () => {
    setError(null)
    if (isMock) {
      setKakaoLoading(true)
      try {
        const { data } = await client.post<ApiResponse<AuthLoginResponse>>('/auth/kakao', {})
        const hasKey = await afterLogin(data.data, setUser, setHasApiKey)
        navigate(hasKey ? '/dashboard' : '/settings', { replace: true })
      } catch {
        setError('로그인 중 오류가 발생했습니다. 다시 시도해 주세요.')
      } finally {
        setKakaoLoading(false)
      }
    } else {
      sessionStorage.setItem('oauth_provider', 'kakao')
      window.location.href = getKakaoOAuthUrl()
    }
  }

  const handleNexonApiKey = async () => {
    const key = apiKey.trim()
    if (!key) {
      setError('Nexon API Key를 입력해 주세요.')
      return
    }
    setApiKeyLoading(true)
    setError(null)
    try {
      const { data } = await client.post<ApiResponse<AuthLoginResponse>>('/auth/nexon-apikey', { apiKey: key })
      const hasKey = await afterLogin(data.data, setUser, setHasApiKey)
      navigate(hasKey ? '/dashboard' : '/settings', { replace: true })
    } catch {
      setError('API Key가 올바르지 않거나 넥슨 서버와 통신에 실패했습니다.')
    } finally {
      setApiKeyLoading(false)
    }
  }

  const isLoading = kakaoLoading || apiKeyLoading

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
            onClick={() => void handleKakaoLogin()}
            disabled={isLoading}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#FEE500] py-3 text-sm font-semibold text-[#191919] transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {kakaoLoading ? (
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-[#191919] border-t-transparent" />
            ) : (
              <span>💬</span>
            )}
            카카오로 로그인
          </button>

          <div className="flex items-center gap-3 py-1">
            <span className="h-px flex-1 bg-white/10" />
            <span className="text-xs text-white/30">또는</span>
            <span className="h-px flex-1 bg-white/10" />
          </div>

          <div className="space-y-2">
            <input
              type="text"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && void handleNexonApiKey()}
              placeholder="Nexon Open API Key"
              disabled={isLoading}
              className="w-full rounded-lg border border-white/15 bg-[#1a1a2e] px-3 py-2.5 text-sm text-white placeholder-white/30 outline-none focus:border-white/40 disabled:opacity-50"
            />
            <button
              onClick={() => void handleNexonApiKey()}
              disabled={isLoading}
              className="flex w-full items-center justify-center gap-2 rounded-lg border border-white/20 bg-[#1a1a2e] py-3 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              {apiKeyLoading ? (
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              ) : (
                <span>🎮</span>
              )}
              Nexon API Key로 로그인
            </button>
          </div>
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
